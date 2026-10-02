package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.NotificationRepository;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.UserRepository;
import sisa.service.dto.AnnouncementForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Communication & Notification Management (report FR-11, business rules 1-4): the
 * Principal/Registrar/Teacher's "post an announcement/alert/notice" path, fanning out
 * to one Notification row per resolved recipient. This is the NotificationCenter any
 * module can call for a broadcast; Module 3's single-recipient system alerts keep
 * using the plain Notification constructor directly since they already know their one
 * recipient and don't need scope resolution.
 */
@Service
public class AnnouncementService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    public AnnouncementService(NotificationRepository notificationRepository, UserRepository userRepository,
                               StudentRepository studentRepository, TeacherRepository teacherRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
    }

    /**
     * Creates one broadcast, fanned out to every resolved recipient (business rule 4).
     * Only PRINCIPAL, REGISTRAR and TEACHER may call this (business rules 1-3); a
     * Teacher may only target their own class (business rule 3 — "class notices").
     * Returns how many recipients were reached.
     */
    @Transactional
    public int create(AnnouncementForm form, User sender) {
        if (sender.getRole() != Role.PRINCIPAL && sender.getRole() != Role.REGISTRAR && sender.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Principal, Registrar or a Teacher can post notices.");
        }
        NotificationCategory category = NotificationCategory.valueOf(form.getCategory());
        NotificationScope scope = NotificationScope.valueOf(form.getTargetScope());

        String className = form.getClassName();
        if (sender.getRole() == Role.TEACHER) {
            // A Teacher's notices are always their own class, per business rule 3 — never the whole school
            // or someone else's class, even if the form tried to say otherwise.
            Teacher teacher = teacherRepository.findById(sender.getUserId())
                    .orElseThrow(() -> new IllegalStateException("No teacher record for " + sender.getUserId()));
            if (!teacher.isClassTeacher() || teacher.getAssignedClassName() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a Class Teacher can post class notices.");
            }
            scope = NotificationScope.CLASS;
            className = teacher.getAssignedClassName();
        }

        Set<String> recipients = resolveRecipients(scope, className, form.getStudentId());
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

    /** SCHOOL -> every approved (enabled) user; CLASS -> that class's active students + their linked parents; STUDENT -> that student + their parent; TEACHERS -> every approved teacher. */
    private Set<String> resolveRecipients(NotificationScope scope, String className, String studentId) {
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
        }
        return ids;
    }

    private LocalDateTime parseScheduledFor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDateTime.parse(raw);
    }

    // ---------- reading back what was sent (the "message log") ----------

    public record Broadcast(String broadcastId, String senderUserId, NotificationCategory category, String subject,
                            String body, NotificationScope targetScope, LocalDateTime scheduledFor,
                            LocalDateTime sentAt, LocalDateTime createdAt, int recipientCount) {}

    private Broadcast toBroadcast(List<Notification> rows) {
        Notification first = rows.get(0);
        return new Broadcast(first.getBroadcastId(), first.getSenderUserId(), first.getCategory(), first.getSubject(),
                first.getBody(), first.getTargetScope(), first.getScheduledFor(), first.getSentAt(), first.getCreatedAt(), rows.size());
    }

    /** Every broadcast sent school-wide, newest first — the Principal's oversight log. */
    public List<Broadcast> allBroadcasts() {
        return groupIntoBroadcasts(notificationRepository.findByBroadcastIdIsNotNullOrderByCreatedAtDesc());
    }

    /** Just this sender's own broadcasts — the Registrar/Teacher "sent" log. */
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

    /** One broadcast by id, for the edit form — same ownership rule as edit/delete. */
    public Broadcast getBroadcastForEdit(String broadcastId, User requester) {
        return toBroadcast(requireEditableRows(broadcastId, requester));
    }

    /**
     * Edits a broadcast's subject/body (Update in Communication & Notification
     * Management) — e.g. fixing a typo or a wrong date in a notice. The change is applied
     * to every fanned-out recipient row so everyone sees the corrected text; recipients who
     * had already read it get it flagged unread again so the correction isn't missed.
     * Only the Principal, or the broadcast's own sender, may edit it. Category, scope and
     * recipients are not editable — to reach different people, post a new notice.
     */
    @Transactional
    public String updateBroadcast(String broadcastId, String subject, String body, User requester) {
        return updateBroadcast(broadcastId, subject, body, null, requester);
    }

    /**
     * Same as above, plus rescheduling: while a broadcast is still waiting to go out
     * (no recipient row sent yet), its date/time can be moved. {@code scheduledFor} null
     * leaves the date alone; blank or a time that's already passed sends it right away,
     * exactly like leaving the date empty on the "New Announcement" form. Once it has been
     * sent the date is history and can no longer be changed.
     */
    @Transactional
    public String updateBroadcast(String broadcastId, String subject, String body, String scheduledFor, User requester) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("The message body can't be empty.");
        }
        List<Notification> rows = requireEditableRows(broadcastId, requester);

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

    /**
     * Deletes one notification from the requester's own inbox (Delete in Communication &
     * Notification Management). It only removes that recipient's copy — the sender's log
     * and every other recipient's copy are untouched.
     */
    @Transactional
    public void deleteFromInbox(Long notificationId, User requester) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such notification."));
        if (!notification.getRecipientUserId().equals(requester.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That notification isn't yours.");
        }
        notificationRepository.delete(notification);
    }

    /**
     * Cancels a scheduled broadcast before it goes out (business rule 1's "scheduled
     * announcement" — the flip side of it: undo before NotificationSchedulerService's
     * poller marks it sent). Only the Principal, or the broadcast's own sender, may
     * cancel it, and only while every one of its fanned-out rows is still unsent —
     * once any recipient has it (sentAt set), it can no longer be deleted, only left
     * to stand. Returns the cancelled broadcast's subject, for the confirmation flash.
     */
    @Transactional
    public String deleteScheduledBroadcast(String broadcastId, User requester) {
        List<Notification> rows = notificationRepository.findByBroadcastId(broadcastId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such broadcast.");
        }
        Notification first = rows.get(0);
        if (requester.getRole() != Role.PRINCIPAL && !requester.getUserId().equals(first.getSenderUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own broadcasts.");
        }
        boolean anySent = rows.stream().anyMatch(n -> n.getSentAt() != null);
        if (anySent) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This broadcast has already been sent and can no longer be deleted.");
        }
        String subject = (first.getSubject() != null && !first.getSubject().isBlank()) ? first.getSubject() : "(no subject)";
        notificationRepository.deleteByBroadcastId(broadcastId);
        return subject;
    }
}
