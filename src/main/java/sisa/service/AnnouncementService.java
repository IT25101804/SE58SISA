package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.NotificationRepository;
import sisa.repository.RegistrarRepository;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.TimetableSlotRepository;
import sisa.repository.UserRepository;
import sisa.service.dto.AnnouncementForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnnouncementService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final RegistrarRepository registrarRepository;
    private final ParentTeacherContactService parentTeacherContactService;

    public AnnouncementService(NotificationRepository notificationRepository, UserRepository userRepository,
                               StudentRepository studentRepository, TeacherRepository teacherRepository,
                               TimetableSlotRepository timetableSlotRepository, RegistrarRepository registrarRepository,
                               ParentTeacherContactService parentTeacherContactService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.registrarRepository = registrarRepository;
        this.parentTeacherContactService = parentTeacherContactService;
    }

    @Transactional
    public int create(AnnouncementForm form, User sender) {
        if (sender.getRole() == Role.STUDENT || sender.getRole() == Role.PARENT) {
            // Students and parents can message one of their own (or their child's) teachers.
            if (!"TEACHER".equals(form.getTargetScope())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Students and parents can only message a teacher.");
            }
            List<ParentTeacherContactService.TeacherContact> allowed = sender.getRole() == Role.STUDENT
                    ? parentTeacherContactService.teachersForStudent(sender.getUserId())
                    : parentTeacherContactService.teachersForParent(sender.getUserId());
            if (allowed.stream().noneMatch(t -> t.userId().equals(form.getTeacherId()))) {
                throw new IllegalArgumentException("Choose one of your teachers to send this to.");
            }
        } else if (sender.getRole() != Role.PRINCIPAL && sender.getRole() != Role.REGISTRAR && sender.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't send messages.");
        }
        NotificationCategory category = NotificationCategory.valueOf(form.getCategory());
        boolean noScope = form.getTargetScope() == null || form.getTargetScope().isBlank();
        if (noScope && sender.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("Choose who to send this to.");
        }
        NotificationScope scope = noScope ? NotificationScope.CLASS : NotificationScope.valueOf(form.getTargetScope());

        String className = form.getClassName();
        if (sender.getRole() == Role.TEACHER) {
            Teacher teacher = teacherRepository.findById(sender.getUserId())
                    .orElseThrow(() -> new IllegalStateException("No teacher record for " + sender.getUserId()));
            switch (scope) {
                case CLASS -> {
                    if (!teacher.isClassTeacher() || teacher.getAssignedClassName() == null) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a Class Teacher can post class notices.");
                    }
                    className = teacher.getAssignedClassName();
                }
                case PRINCIPAL -> { }
                case ONE_STUDENT, GUARDIANS -> {
                    Student student = requireStudent(form.getStudentId());
                    if (!classesTaughtBy(teacher).contains(student.getClassName())) {
                        throw new IllegalArgumentException("Student " + student.getStudentId()
                                + " isn't in a class you teach.");
                    }
                }
                default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Teachers can send to their class, the Principal, one student or one parent.");
            }
        }

        Set<String> recipients = resolveRecipients(scope, className, form.getStudentId(), form.getTeacherId(), form.getRegistrarId());
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("No recipients found for that target.");
        }

        LocalDateTime scheduledFor = parseScheduledFor(form.getScheduledFor());
        LocalDateTime now = LocalDateTime.now();
        boolean sendNow = scheduledFor == null || !scheduledFor.isAfter(now);
        String broadcastId = UUID.randomUUID().toString();

        for (String recipientId : recipients) {
            Notification notification = new Notification();
            notification.setRecipientUserId(recipientId);
            notification.setSenderUserId(sender.getUserId());
            notification.setCategory(category);
            notification.setSubject(form.getSubject());
            notification.setBody(form.getBody());
            notification.setTargetScope(scope);
            notification.setBroadcastId(broadcastId);
            notification.setScheduledFor(scheduledFor);
            notification.setSentAt(sendNow ? now : null);
            notificationRepository.save(notification);
        }
        return recipients.size();
    }

    private Set<String> resolveRecipients(NotificationScope scope, String className, String studentId, String teacherId,
                                          String registrarId) {
        Set<String> ids = new LinkedHashSet<>();
        switch (scope) {
            case SCHOOL -> userRepository.findByStatus(AccountStatus.APPROVED).forEach(u -> ids.add(u.getUserId()));
            case TEACHERS -> teacherRepository.findAll().stream()
                    .filter(t -> t.getUser() != null && t.getUser().getStatus() == AccountStatus.APPROVED)
                    .forEach(t -> ids.add(t.getTeacherId()));
            case CLASS -> {
                if (className == null || className.isBlank()) {
                    throw new IllegalArgumentException("A class is required for a class-scoped notice.");
                }
                for (Student student : studentRepository.findByClassNameAndStatusOrderByUser_FullNameAsc(className, StudentStatus.ACTIVE)) {
                    ids.add(student.getStudentId());
                    if (student.getParent() != null) ids.add(student.getParent().getUserId());
                }
            }
            case STUDENT -> {
                if (studentId == null || studentId.isBlank()) {
                    throw new IllegalArgumentException("A student is required for a student-scoped notice.");
                }
                Student student = studentRepository.findById(studentId)
                        .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId));
                ids.add(student.getStudentId());
                if (student.getParent() != null) ids.add(student.getParent().getUserId());
            }
            case TEACHER -> {
                if (teacherId == null || teacherId.isBlank()) {
                    throw new IllegalArgumentException("Choose a teacher to send this to.");
                }
                Teacher teacher = teacherRepository.findById(teacherId.trim())
                        .orElseThrow(() -> new IllegalArgumentException("No such teacher: " + teacherId));
                if (teacher.getUser() == null || teacher.getUser().getStatus() != AccountStatus.APPROVED) {
                    throw new IllegalArgumentException("That teacher's account isn't active.");
                }
                ids.add(teacher.getTeacherId());
            }
            case REGISTRARS -> activeRegistrars().forEach(r -> ids.add(r.getRegistrarId()));
            case REGISTRAR -> {
                if (registrarId == null || registrarId.isBlank()) {
                    throw new IllegalArgumentException("Choose a registrar to send this to.");
                }
                Registrar registrar = registrarRepository.findById(registrarId.trim())
                        .orElseThrow(() -> new IllegalArgumentException("No such registrar: " + registrarId));
                if (registrar.getUser() == null || registrar.getUser().getStatus() != AccountStatus.APPROVED) {
                    throw new IllegalArgumentException("That registrar's account isn't active.");
                }
                ids.add(registrar.getRegistrarId());
            }
            case PRINCIPAL -> userRepository.findByRole(Role.PRINCIPAL).stream()
                    .filter(u -> u.getStatus() == AccountStatus.APPROVED)
                    .forEach(u -> ids.add(u.getUserId()));
            case ONE_STUDENT -> ids.add(requireStudent(studentId).getStudentId());
            case GUARDIANS -> {
                Student student = requireStudent(studentId);
                if (student.getParent() == null) {
                    throw new IllegalArgumentException("Student " + student.getStudentId() + " has no linked parent/guardian.");
                }
                ids.add(student.getParent().getUserId());
            }
        }
        return ids;
    }

    /** Approved registrars, A-Z by name — the choices for a "One Registrar" message. */
    public List<Registrar> activeRegistrars() {
        return registrarRepository.findAll().stream()
                .filter(r -> r.getUser() != null && r.getUser().getStatus() == AccountStatus.APPROVED)
                .sorted(Comparator.comparing(r -> r.getUser().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Teacher> activeTeachers() {
        return teacherRepository.findAll().stream()
                .filter(t -> t.getUser() != null && t.getUser().getStatus() == AccountStatus.APPROVED)
                .sorted(Comparator.comparing(t -> t.getUser().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Set<String> classesTaughtBy(Teacher teacher) {
        Set<String> classes = new HashSet<>();
        if (teacher.isClassTeacher() && teacher.getAssignedClassName() != null) classes.add(teacher.getAssignedClassName());
        timetableSlotRepository.findByTeacher_TeacherIdOrderByPeriodNumberAsc(teacher.getTeacherId())
                .forEach(slot -> classes.add(slot.getClassName()));
        return classes;
    }

    private Student requireStudent(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Type the student's ID (e.g. S2600001).");
        }
        return studentRepository.findById(studentId.trim())
                .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId.trim()));
    }

    private LocalDateTime parseScheduledFor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDateTime.parse(raw);
    }

    public record Broadcast(String broadcastId, String senderUserId, NotificationCategory category, String subject,
                            String body, NotificationScope targetScope, LocalDateTime scheduledFor,
                            LocalDateTime sentAt, LocalDateTime createdAt, int recipientCount) {}

    private Broadcast toBroadcast(List<Notification> rows) {
        Notification first = rows.get(0);
        return new Broadcast(first.getBroadcastId(), first.getSenderUserId(), first.getCategory(), first.getSubject(),
                first.getBody(), first.getTargetScope(), first.getScheduledFor(), first.getSentAt(), first.getCreatedAt(), rows.size());
    }

    public List<Broadcast> allBroadcasts() {
        return groupIntoBroadcasts(notificationRepository.findByBroadcastIdIsNotNullOrderByCreatedAtDesc());
    }

    public List<Broadcast> broadcastsBySender(String senderUserId) {
        return groupIntoBroadcasts(notificationRepository.findBySenderUserIdAndBroadcastIdIsNotNullOrderByCreatedAtDesc(senderUserId));
    }

    private List<Broadcast> groupIntoBroadcasts(List<Notification> rows) {
        Map<String, List<Notification>> byBroadcast = new LinkedHashMap<>();
        for (Notification n : rows) byBroadcast.computeIfAbsent(n.getBroadcastId(), k -> new ArrayList<>()).add(n);
        List<Broadcast> broadcasts = new ArrayList<>();
        for (List<Notification> group : byBroadcast.values()) broadcasts.add(toBroadcast(group));
        return broadcasts;
    }

    public Broadcast getBroadcastForEdit(String broadcastId, User requester) {
        return toBroadcast(requireEditableRows(broadcastId, requester));
    }

    @Transactional
    public String updateBroadcast(String broadcastId, String subject, String body, User requester) {
        return updateBroadcast(broadcastId, subject, body, null, requester);
    }

    @Transactional
    public String updateBroadcast(String broadcastId, String subject, String body, String scheduledFor, User requester) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("The message body can't be empty.");
        }
        List<Notification> rows = requireEditableRows(broadcastId, requester);
        if (rows.stream().anyMatch(n -> n.getSentAt() != null)) {
            throw new IllegalArgumentException("This message has already been sent, so it can no longer be edited. You can delete it instead.");
        }

        boolean reschedule = scheduledFor != null;
        LocalDateTime newSchedule = null;
        boolean sendNow = false;
        LocalDateTime now = LocalDateTime.now();
        if (reschedule) {
            if (rows.stream().anyMatch(n -> n.getSentAt() != null)) {
                throw new IllegalArgumentException("This broadcast has already been sent, so its date can no longer be changed.");
            }
            try {
                newSchedule = parseScheduledFor(scheduledFor);
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException("\"" + scheduledFor + "\" isn't a valid date and time.");
            }
            sendNow = newSchedule == null || !newSchedule.isAfter(now);
        }

        for (Notification n : rows) {
            n.setSubject(subject);
            n.setBody(body);
            if (reschedule) {
                n.setScheduledFor(newSchedule);
                n.setSentAt(sendNow ? now : null);
            }
            if (n.getSentAt() != null) n.setRead(false);
        }
        notificationRepository.saveAll(rows);
        return (subject != null && !subject.isBlank()) ? subject : "(no subject)";
    }

    private List<Notification> requireEditableRows(String broadcastId, User requester) {
        List<Notification> rows = notificationRepository.findByBroadcastId(broadcastId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such broadcast.");
        }
        if (requester.getRole() != Role.PRINCIPAL && !requester.getUserId().equals(rows.get(0).getSenderUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own broadcasts.");
        }
        return rows;
    }

    @Transactional
    public void deleteFromInbox(Long notificationId, User requester) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such notification."));
        if (!notification.getRecipientUserId().equals(requester.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That notification isn't yours.");
        }
        notificationRepository.delete(notification);
    }

    @Transactional
    public String deleteBroadcast(String broadcastId, User requester) {
        List<Notification> rows = notificationRepository.findByBroadcastId(broadcastId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such broadcast.");
        }
        Notification first = rows.get(0);
        if (requester.getRole() != Role.PRINCIPAL && !requester.getUserId().equals(first.getSenderUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own broadcasts.");
        }
        String subject = (first.getSubject() != null && !first.getSubject().isBlank()) ? first.getSubject() : "(no subject)";
        notificationRepository.deleteByBroadcastId(broadcastId);
        return subject;
    }
}
