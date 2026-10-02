package sisa.controller;

import sisa.entity.AccountStatus;
import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account creation (Student and Parent alike) is the Registrar's job alone (report
 * section 6.2/6.3) — the Principal's role in User & Access is to approve/reject/
 * disable/reset accounts, never to create them. The Principal keeps full read/list/
 * edit access to /registrar/students otherwise; only the two creation endpoints are
 * off-limits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrarStudentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

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

    @Test
    void principalCannotRegisterAStudentButCanStillViewTheStudentList() throws Exception {
        createUser("PRINCIPAL2698", "principal2698", Role.PRINCIPAL);

        mockMvc.perform(get("/registrar/students/new").with(user("principal2698").roles("PRINCIPAL")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/registrar/students/new")
                        .with(user("principal2698").roles("PRINCIPAL"))
                        .with(csrf())
                        .param("fullName", "Blocked Student")
                        .param("email", "blockedstudent@test.local")
                        .param("rawPassword", "Password123")
                        .param("className", "Grade 5 - A")
                        .param("guardianFullName", "Blocked Guardian")
                        .param("guardianEmail", "blockedguardian@test.local")
                        .param("guardianRawPassword", "Password123")
                        .param("relationshipToStudent", "Mother"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/registrar/students").with(user("principal2698").roles("PRINCIPAL")))
                .andExpect(status().isOk());

        assertThat(userRepository.findAll().stream().anyMatch(u -> "Blocked Student".equals(u.getFullName())))
                .isFalse();
    }

    @Test
    void principalCannotCreateAStandaloneParentAccount() throws Exception {
        createUser("PRINCIPAL2697", "principal2697", Role.PRINCIPAL);

        mockMvc.perform(get("/registrar/accounts/new-parent").with(user("principal2697").roles("PRINCIPAL")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/registrar/accounts/new-parent")
                        .with(user("principal2697").roles("PRINCIPAL"))
                        .with(csrf())
                        .param("fullName", "Blocked Parent")
                        .param("email", "blockedparent@test.local")
                        .param("rawPassword", "Password123"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findAll().stream().anyMatch(u -> "Blocked Parent".equals(u.getFullName())))
                .isFalse();
    }

    @Test
    void registrarCanStillRegisterAStudent() throws Exception {
        createUser("REG2696", "registrar2696", Role.REGISTRAR);

        mockMvc.perform(post("/registrar/students/new")
                        .with(user("registrar2696").roles("REGISTRAR"))
                        .with(csrf())
                        .param("fullName", "Allowed Student")
                        .param("email", "allowedstudent@test.local")
                        .param("rawPassword", "Password123")
                        .param("className", "Grade 5 - A")
                        .param("guardianFullName", "Allowed Guardian")
                        .param("guardianEmail", "allowedguardian@test.local")
                        .param("guardianRawPassword", "Password123")
                        .param("relationshipToStudent", "Mother"))
                .andExpect(status().isOk());

        assertThat(userRepository.findAll().stream().anyMatch(u -> "Allowed Student".equals(u.getFullName())))
                .isTrue();
    }
}
