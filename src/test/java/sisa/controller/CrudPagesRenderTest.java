package sisa.controller;

import sisa.entity.*;
import sisa.entity.*;
import sisa.report.ReportType;
import sisa.repository.NotificationRepository;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.AnnouncementService;
import sisa.service.SavedReportService;
import sisa.service.dto.AnnouncementForm;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrudPagesRenderTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AnnouncementService announcementService;
    @Autowired private SavedReportService savedReportService;

    private User principal() {
        return userRepository.findByUsername("principal").orElseThrow();
    }

    private User createUser(String userId, Role role, AccountStatus status) {
        User u = new User();
        u.setUserId(userId);
        u.setUsername(userId.toLowerCase());
        u.setPassword("hashed");
        u.setFullName(userId + " Full Name");
        u.setEmail(userId + "@test.local");
        u.setRole(role);
        u.setStatus(status);
        return userRepository.save(u);
    }

    @Test
    void reportPagesShowSavedReportsCard() throws Exception {
        savedReportService.create("My enrolment view", ReportType.ENROLMENT, null, null, null, principal());
        mockMvc.perform(get("/principal/reports").with(user("principal").roles("PRINCIPAL")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Saved Reports")))
                .andExpect(content().string(containsString("My enrolment view")));

        createUser("RX910001", Role.REGISTRAR, AccountStatus.APPROVED);
        mockMvc.perform(get("/registrar/reports").with(user("rx910001").roles("REGISTRAR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Saved Reports")));
    }

    @Test
    void savedReportCreateOpenDeleteRoutes() throws Exception {
        mockMvc.perform(post("/principal/reports/saved").with(user("principal").roles("PRINCIPAL")).with(csrf())
                        .param("name", "Via form").param("type", "CLASS_LIST").param("className", "Grade 10 - A"))
                .andExpect(status().is3xxRedirection());
        SavedReport saved = savedReportService.listFor(principal()).stream()
                .filter(r -> r.getName().equals("Via form")).findFirst().orElseThrow();

        mockMvc.perform(get("/principal/reports/saved/" + saved.getId()).with(user("principal").roles("PRINCIPAL")))
                .andExpect(redirectedUrl("/principal/reports?type=CLASS_LIST&className=Grade%2010%20-%20A"));

        mockMvc.perform(post("/principal/reports/saved/" + saved.getId() + "/delete")
                        .with(user("principal").roles("PRINCIPAL")).with(csrf()))
                .andExpect(redirectedUrl("/principal/reports"));
        assertThat(savedReportService.listFor(principal())).extracting(SavedReport::getId).doesNotContain(saved.getId());
    }

    @Test
    void accountsAndStudentDetailPagesRenderDeleteButtons() throws Exception {
        createUser("PX910001", Role.PARENT, AccountStatus.REJECTED);
        mockMvc.perform(get("/principal/accounts").with(user("principal").roles("PRINCIPAL")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/principal/accounts/PX910001/delete")));

        Student s = new Student();
        s.setStudentId("SX910001");
        s.setUser(createUser("SX910001", Role.STUDENT, AccountStatus.PENDING));
        s.setStatus(StudentStatus.ACTIVE);
        studentRepository.save(s);
        mockMvc.perform(get("/registrar/students/SX910001").with(user("principal").roles("PRINCIPAL")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/registrar/students/SX910001/delete")));

        mockMvc.perform(post("/registrar/students/SX910001/delete").with(user("principal").roles("PRINCIPAL")).with(csrf()))
                .andExpect(redirectedUrl("/registrar/students"));
        assertThat(studentRepository.findById("SX910001")).isEmpty();
    }

    @Test
    void broadcastEditPageAndInboxDelete() throws Exception {
        User recipient = createUser("TX910001", Role.TEACHER, AccountStatus.APPROVED);
        AnnouncementForm form = new AnnouncementForm();
        form.setCategory("ANNOUNCEMENT");
        form.setSubject("Staff meeting");
        form.setBody("Staff meeting at 2pm.");
        form.setTargetScope("SCHOOL");
        form.setScheduledFor(java.time.LocalDateTime.now().plusDays(2).withSecond(0).withNano(0).toString());
        announcementService.create(form, principal());
        Notification row = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipient.getUserId()).get(0);

        mockMvc.perform(get("/principal/comms").with(user("principal").roles("PRINCIPAL")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/comms/broadcasts/" + row.getBroadcastId() + "/edit")));
        mockMvc.perform(get("/comms/broadcasts/" + row.getBroadcastId() + "/edit").with(user("principal").roles("PRINCIPAL")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Staff meeting at 2pm.")));
        mockMvc.perform(post("/comms/broadcasts/" + row.getBroadcastId() + "/edit").with(user("principal").roles("PRINCIPAL")).with(csrf())
                        .param("subject", "Staff meeting").param("body", "Staff meeting moved to 3pm.").param("scheduledFor", ""))
                .andExpect(redirectedUrl("/principal/comms"));

        mockMvc.perform(get("/inbox/" + row.getId()).with(user("tx910001").roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("moved to 3pm")))
                .andExpect(content().string(containsString("/inbox/" + row.getId() + "/delete")));
        mockMvc.perform(post("/inbox/" + row.getId() + "/delete").with(user("tx910001").roles("TEACHER")).with(csrf()))
                .andExpect(redirectedUrl("/inbox"));
        assertThat(notificationRepository.findById(row.getId())).isEmpty();
    }
}
