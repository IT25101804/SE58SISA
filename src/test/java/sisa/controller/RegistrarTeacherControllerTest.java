package sisa.controller;

import sisa.entity.*;
import sisa.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import sisa.entity.AccountStatus;
import sisa.entity.Role;
import sisa.entity.Teacher;
import sisa.entity.User;
import sisa.repository.TeacherRepository;
import sisa.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the previously-missing "create a Teacher account, then assign them a subject
 * specialty and (at most one) class" capability (System Functions doc: User & Access
 * -> Registrar "Create ... teacher ... accounts"; Academic Management -> Registrar
 * "Assign teachers to subjects... change subject-teacher assignments"). Before this,
 * there was no way to create a Teacher account through the UI at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrarTeacherControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherRepository teacherRepository;

    private User createUser(String userId, String username, Role role) {
        User u = new User();
        u.setUserId(userId);
        u.setUsername(username);
        u.setPassword("hashed");
        u.setFullName(username + " Full Name");
        u.setEmail(username + "@test.local");
        u.setRole(role);
        u.setStatus(AccountStatus.APPROVED);
        return userRepository.save(u);
    }

    private Teacher createTeacher(String teacherId, String username) {
        User teacherUser = createUser(teacherId, username, Role.TEACHER);
        Teacher teacher = new Teacher();
        teacher.setTeacherId(teacherId);
        teacher.setUser(teacherUser);
        return teacherRepository.save(teacher);
    }

    @Test
    void registrarCanCreateATeacherAccountAndItStaysPendingUntilApproved() throws Exception {
        createUser("REG2699501", "registrar501", Role.REGISTRAR);

        mockMvc.perform(post("/registrar/teachers/new")
                        .with(user("registrar501").roles("REGISTRAR"))
                        .with(csrf())
                        .param("fullName", "New Teacher")
                        .param("email", "newteacher501@test.local")
                        .param("rawPassword", "Password123"))
                .andExpect(status().isOk());

        User created = userRepository.findAll().stream()
                .filter(u -> "New Teacher".equals(u.getFullName()))
                .findFirst().orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.TEACHER);
        assertThat(created.getStatus()).isEqualTo(AccountStatus.PENDING);
        // Username is never chosen — it's the auto-generated ID itself, since that's unique.
        assertThat(created.getUsername()).isEqualTo(created.getUserId());
        assertThat(teacherRepository.findById(created.getUserId())).isPresent();
    }

    @Test
    void registrarCanAssignASubjectSpecialtyAndClassToATeacher() throws Exception {
        createUser("REG2699502", "registrar502", Role.REGISTRAR);
        Teacher teacher = createTeacher("T2699502", "teacher502");

        mockMvc.perform(post("/registrar/teachers/{id}/edit", teacher.getTeacherId())
                        .with(user("registrar502").roles("REGISTRAR"))
                        .with(csrf())
                        .param("subjectSpecialty", "Mathematics")
                        .param("joiningYear", "2026")
                        .param("classTeacher", "true")
                        .param("assignedClassName", "9A"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        Teacher updated = teacherRepository.findById(teacher.getTeacherId()).orElseThrow();
        assertThat(updated.getSubjectSpecialty()).isEqualTo("Mathematics");
        assertThat(updated.isClassTeacher()).isTrue();
        assertThat(updated.getAssignedClassName()).isEqualTo("9A");
    }

    @Test
    void assigningASecondClassTeacherToAnAlreadyAssignedClassIsRejected() throws Exception {
        createUser("REG2699503", "registrar503", Role.REGISTRAR);
        Teacher existing = createTeacher("T2699503", "teacher503existing");
        existing.setClassTeacher(true);
        existing.setAssignedClassName("10B");
        teacherRepository.save(existing);

        Teacher incoming = createTeacher("T2699504", "teacher504incoming");

        mockMvc.perform(post("/registrar/teachers/{id}/edit", incoming.getTeacherId())
                        .with(user("registrar503").roles("REGISTRAR"))
                        .with(csrf())
                        .param("subjectSpecialty", "Science")
                        .param("classTeacher", "true")
                        .param("assignedClassName", "10B"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("teacher503existing Full Name")));

        Teacher stillUnassigned = teacherRepository.findById(incoming.getTeacherId()).orElseThrow();
        assertThat(stillUnassigned.isClassTeacher()).isFalse();
    }

    @Test
    void studentCannotAccessRegistrarTeacherManagement() throws Exception {
        createUser("S2699505", "student505", Role.STUDENT);

        mockMvc.perform(get("/registrar/teachers").with(user("student505").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    /**
     * Account creation is the Registrar's job alone (report section 6.2/6.3) — the
     * Principal's role in User & Access is to approve/reject/disable/reset, never to
     * create accounts. The Principal keeps full read/list access to /registrar/**
     * otherwise (e.g. GET /registrar/teachers), just not this one creation endpoint.
     */
    @Test
    void principalCannotCreateATeacherAccountButCanStillViewTheTeacherList() throws Exception {
        createUser("PRINCIPAL2699", "principal2699", Role.PRINCIPAL);

        mockMvc.perform(post("/registrar/teachers/new")
                        .with(user("principal2699").roles("PRINCIPAL"))
                        .with(csrf())
                        .param("fullName", "Blocked Teacher")
                        .param("email", "blocked@test.local")
                        .param("rawPassword", "Password123"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/registrar/teachers/new").with(user("principal2699").roles("PRINCIPAL")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/registrar/teachers").with(user("principal2699").roles("PRINCIPAL")))
                .andExpect(status().isOk());

        assertThat(userRepository.findAll().stream().anyMatch(u -> "Blocked Teacher".equals(u.getFullName())))
                .isFalse();
    }
}
