package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.ParentRepository;
import sisa.repository.ResourceRepository;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import sisa.service.dto.*;

import java.time.LocalDate;
import java.util.List;

@Service
public class StudentRegistrationService {

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final UserAccountFactory userAccountFactory;
    private final AuditLogService auditLogService;
    private final ResourceRepository resourceRepository;

    public StudentRegistrationService(StudentRepository studentRepository,
                                      ParentRepository parentRepository,
                                      UserRepository userRepository,
                                      UserAccountFactory userAccountFactory,
                                      AuditLogService auditLogService,
                                      ResourceRepository resourceRepository) {
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.userRepository = userRepository;
        this.userAccountFactory = userAccountFactory;
        this.auditLogService = auditLogService;
        this.resourceRepository = resourceRepository;
    }

    public record ClassOption(String name, Integer capacity, long enrolled) {
        public boolean isFull() { return capacity != null && enrolled >= capacity; }
    }

    public List<ClassOption> classOptions() {
        return resourceRepository.findByTypeOrderByNameAsc(ResourceType.CLASSROOM).stream()
                .map(r -> new ClassOption(r.getName(), r.getCapacity(),
                        studentRepository.countByClassNameIgnoreCaseAndStatus(r.getName(), StudentStatus.ACTIVE)))
                .toList();
    }

    private void assertClassHasSpace(String className, String studentIdBeingEdited) {
        if (!StringUtils.hasText(className)) return;
        String trimmed = className.trim();
        resourceRepository.findByTypeAndNameIgnoreCase(ResourceType.CLASSROOM, trimmed).ifPresent(classroom -> {
            if (classroom.getCapacity() == null) return;
            long enrolled = studentRepository.countByClassNameIgnoreCaseAndStatus(trimmed, StudentStatus.ACTIVE);
            if (studentIdBeingEdited != null) {
                Student current = studentRepository.findById(studentIdBeingEdited).orElse(null);
                if (current != null && current.getStatus() == StudentStatus.ACTIVE
                        && trimmed.equalsIgnoreCase(current.getClassName())) {
                    enrolled = Math.max(0, enrolled - 1);
                }
            }
            if (enrolled >= classroom.getCapacity()) {
                throw new IllegalArgumentException(classroom.getName() + " is already at full capacity ("
                        + classroom.getCapacity() + "/" + classroom.getCapacity()
                        + "). Choose a different class, or ask the Principal to raise its capacity from Labs & Classrooms Availability.");
            }
        });
    }

    @Transactional
    public StudentRegistrationResult register(StudentRegistrationRequest req, User registeredBy) {
        requireRegistrarOrPrincipal(registeredBy);
        assertClassHasSpace(req.getClassName(), null);

        boolean parentReused;
        User parentUser;
        Parent parent;

        String guardianUsername = req.getGuardianUsername() == null ? "" : req.getGuardianUsername().trim();
        var existing = StringUtils.hasText(guardianUsername) ? userRepository.findByUsername(guardianUsername) : java.util.Optional.<User>empty();

        if (existing.isPresent()) {
            parentUser = existing.get();
            if (parentUser.getRole() != Role.PARENT) {
                throw new IllegalArgumentException("ID \"" + guardianUsername + "\" already belongs to a non-parent account.");
            }
            parent = parentRepository.findById(parentUser.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Parent account " + parentUser.getUserId() + " has no Parent record."));
            parentReused = true;
        } else {
            CreateAccountRequest parentReq = new CreateAccountRequest();
            parentReq.setFullName(req.getGuardianFullName());
            parentReq.setEmail(req.getGuardianEmail());
            parentReq.setRawPassword(req.getGuardianRawPassword());
            parentUser = userAccountFactory.createFor(Role.PARENT, parentReq);
            parent = parentRepository.findById(parentUser.getUserId()).orElseThrow();
            parent.setRelationshipToStudent(req.getRelationshipToStudent());
            parent.setContactNumber(req.getGuardianContact());
            parentRepository.save(parent);
            parentReused = false;
        }

        CreateAccountRequest studentReq = new CreateAccountRequest();
        studentReq.setFullName(req.getFullName());
        studentReq.setEmail(req.getEmail());
        studentReq.setRawPassword(req.getRawPassword());
        User studentUser = userAccountFactory.createFor(Role.STUDENT, studentReq);

        Student student = studentRepository.findById(studentUser.getUserId()).orElseThrow();
        student.setAdmissionYear(req.getAdmissionYear());
        student.setClassName(req.getClassName());
        student.setDateOfBirth(parseDate(req.getDateOfBirth()));
        student.setGender(req.getGender());
        student.setAddress(req.getAddress());
        student.setEmergencyContact(req.getEmergencyContact());
        student.setEnrollmentDate(LocalDate.now());
        student.setStatus(StudentStatus.ACTIVE);
        student.setParent(parentUser);
        student.setGuardianName(parentUser.getFullName());
        student.setGuardianContact(parent.getContactNumber());
        studentRepository.save(student);

        auditLogService.log(student.getStudentId(), registeredBy.getUserId(), "REGISTER_STUDENT",
                registeredBy.getFullName() + " registered student " + student.getStudentId()
                        + (parentReused ? " and linked existing guardian " + parentUser.getUserId()
                                        : " and created guardian " + parentUser.getUserId()));

        return new StudentRegistrationResult(student.getStudentId(), parentUser.getUserId(), parentReused);
    }

    public List<Student> search(String q, StudentStatus status) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        return studentRepository.search(query, status);
    }

    public Student getOrThrow(String studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId));
    }

    public Parent getGuardianOrNull(Student student) {
        if (student.getParent() == null) return null;
        return parentRepository.findById(student.getParent().getUserId()).orElse(null);
    }

    @Transactional
    public void updateStudentDetails(String studentId, StudentEditRequest req, User actingUser) {
        requireRegistrarOrPrincipal(actingUser);
        Student student = getOrThrow(studentId);
        assertClassHasSpace(req.getClassName(), studentId);

        User user = student.getUser();
        user.setFullName(req.getFullName());
        user.setEmail(req.getEmail());
        userRepository.save(user);

        student.setAdmissionYear(req.getAdmissionYear());
        student.setClassName(req.getClassName());
        student.setDateOfBirth(parseDate(req.getDateOfBirth()));
        student.setGender(req.getGender());
        student.setAddress(req.getAddress());
        student.setEmergencyContact(req.getEmergencyContact());
        studentRepository.save(student);

        auditLogService.log(studentId, actingUser.getUserId(), "UPDATE_STUDENT",
                actingUser.getFullName() + " updated details for " + studentId);
    }

    @Transactional
    public void updateGuardianDetails(String studentId, GuardianEditRequest req, User actingUser) {
        requireRegistrarOrPrincipal(actingUser);
        Student student = getOrThrow(studentId);
        if (student.getParent() == null) {
            throw new IllegalStateException("Student " + studentId + " has no linked guardian.");
        }
        Parent parent = parentRepository.findById(student.getParent().getUserId())
                .orElseThrow(() -> new IllegalStateException("Linked guardian has no Parent record."));
        parent.setRelationshipToStudent(req.getRelationshipToStudent());
        parent.setContactNumber(req.getContactNumber());
        parentRepository.save(parent);

        student.setGuardianContact(parent.getContactNumber());
        studentRepository.save(student);

        auditLogService.log(studentId, actingUser.getUserId(), "UPDATE_GUARDIAN",
                actingUser.getFullName() + " updated guardian details for " + studentId);
    }

    @Transactional
    public void transferOut(String studentId, String note, User actingUser) {
        requireRegistrarOrPrincipal(actingUser);
        Student student = getOrThrow(studentId);
        if (student.getStatus() == StudentStatus.ARCHIVED) {
            throw new IllegalStateException("Archived students cannot be transferred.");
        }
        student.setStatus(StudentStatus.TRANSFERRED);
        student.setStatusNote(note);
        studentRepository.save(student);
        auditLogService.log(studentId, actingUser.getUserId(), "TRANSFER_OUT",
                actingUser.getFullName() + " marked " + studentId + " as transferred out of SISA"
                        + (StringUtils.hasText(note) ? " — " + note : ""));
    }

    @Transactional
    public void transferIn(String studentId, String note, User actingUser) {
        requireRegistrarOrPrincipal(actingUser);
        Student student = getOrThrow(studentId);
        if (student.getStatus() == StudentStatus.ARCHIVED) {
            throw new IllegalStateException("Archived students cannot be reinstated this way.");
        }
        student.setStatus(StudentStatus.ACTIVE);
        student.setStatusNote(note);
        studentRepository.save(student);
        auditLogService.log(studentId, actingUser.getUserId(), "TRANSFER_IN",
                actingUser.getFullName() + " marked " + studentId + " as transferred into SISA / reinstated"
                        + (StringUtils.hasText(note) ? " — " + note : ""));
    }

    @Transactional
    public void archive(String studentId, String note, User actingUser) {
        requireRegistrarOrPrincipal(actingUser);
        Student student = getOrThrow(studentId);
        student.setStatus(StudentStatus.ARCHIVED);
        student.setStatusNote(StringUtils.hasText(note) ? note : "Graduated");
        studentRepository.save(student);
        auditLogService.log(studentId, actingUser.getUserId(), "ARCHIVE",
                actingUser.getFullName() + " archived " + studentId + " — " + student.getStatusNote());
    }

    private void requireRegistrarOrPrincipal(User user) {
        if (user == null || (user.getRole() != Role.REGISTRAR && user.getRole() != Role.PRINCIPAL)) {
            throw new IllegalStateException("Only the Registrar or Principal can manage student records.");
        }
    }

    private LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        return LocalDate.parse(raw);
    }
}
