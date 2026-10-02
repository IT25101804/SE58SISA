package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.ParentRepository;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.dto.StudentRegistrationRequest;
import sisa.service.dto.StudentRegistrationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the business rules unique to Module 2 (report FR-03, FR-04, section 6.3):
 * auto-generated + auto-linked Parent accounts, guardian reuse for siblings, and
 * archiving as a soft-delete. Each test runs in its own rolled-back transaction, so
 * IdGeneratorService's sequential counters stay isolated between tests.
 */
@SpringBootTest
@Transactional
class StudentRegistrationServiceTest {

    @Autowired
    private StudentRegistrationService studentRegistrationService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ParentRepository parentRepository;
    @Autowired
    private UserRepository userRepository;

    private User registrar() {
        User u = new User();
        u.setUserId("R2699999");
        u.setUsername("test-registrar-" + System.nanoTime());
        u.setPassword("hashed");
        u.setFullName("Test Registrar");
        u.setEmail("registrar@test.local");
        u.setRole(Role.REGISTRAR);
        u.setStatus(AccountStatus.APPROVED);
        return userRepository.save(u);
    }

    /**
     * @param tag                used only to keep each test's email addresses distinct/readable
     * @param existingGuardianId an existing guardian's ID to reuse (report section 6.3's sibling
     *                           case), or null/blank to create a brand new guardian. Usernames are
     *                           never chosen — every account's username is its auto-generated ID.
     */
    private StudentRegistrationRequest baseRequest(String tag, String existingGuardianId) {
        StudentRegistrationRequest req = new StudentRegistrationRequest();
        req.setFullName("Alex Student");
        req.setEmail(tag + "@test.local");
        req.setRawPassword("Password123");
        req.setClassName("Grade 5 - A");
        req.setAdmissionYear("2026");
        req.setGuardianUsername(existingGuardianId);
        req.setGuardianFullName("Jamie Guardian");
        req.setGuardianEmail(tag + "-guardian@test.local");
        req.setGuardianRawPassword("Password123");
        req.setRelationshipToStudent("Mother");
        req.setGuardianContact("0771234567");
        return req;
    }

    @Test
    void registeringStudentCreatesOneStudentAndOneLinkedPendingParentWithSequentialIds() {
        User registrar = registrar();
        StudentRegistrationRequest req = baseRequest("alex.student", null);

        StudentRegistrationResult result = studentRegistrationService.register(req, registrar);

        assertThat(result.studentId()).matches("S\\d{7}");
        assertThat(result.parentId()).matches("P\\d{7}");
        assertThat(result.parentReused()).isFalse();

        Student student = studentRepository.findById(result.studentId()).orElseThrow();
        assertThat(student.getUser().getStatus()).isEqualTo(AccountStatus.PENDING);
        // Username is never chosen — it's the auto-generated ID itself, since that's unique.
        assertThat(student.getUser().getUsername()).isEqualTo(result.studentId());
        assertThat(student.getParent().getUserId()).isEqualTo(result.parentId());

        Parent parent = parentRepository.findById(result.parentId()).orElseThrow();
        assertThat(parent.getUser().getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(parent.getUser().getUsername()).isEqualTo(result.parentId());
    }

    @Test
    void registeringSecondChildForSameGuardianReusesExistingParent() {
        User registrar = registrar();

        StudentRegistrationResult firstResult =
                studentRegistrationService.register(baseRequest("first.child", null), registrar);
        // The Registrar links the sibling to the same guardian by typing in the guardian's ID
        // from the first registration — there's no "chosen username" to reuse anymore.
        StudentRegistrationResult secondResult =
                studentRegistrationService.register(baseRequest("second.child", firstResult.parentId()), registrar);

        assertThat(secondResult.parentReused()).isTrue();
        assertThat(secondResult.parentId()).isEqualTo(firstResult.parentId());
        assertThat(studentRepository.findByParent_UserId(firstResult.parentId())).hasSize(2);
    }

    @Test
    void archivingStudentChangesStatusButKeepsTheRow() {
        User registrar = registrar();
        StudentRegistrationResult result =
                studentRegistrationService.register(baseRequest("grad.student", null), registrar);

        studentRegistrationService.archive(result.studentId(), "Graduated 2026", registrar);

        Student archived = studentRepository.findById(result.studentId()).orElseThrow();
        assertThat(archived.getStatus()).isEqualTo(StudentStatus.ARCHIVED);
        assertThat(archived.getStatusNote()).isEqualTo("Graduated 2026");
    }
}
