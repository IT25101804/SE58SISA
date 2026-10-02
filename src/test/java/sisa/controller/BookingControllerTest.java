package sisa.controller;

import sisa.entity.*;
import sisa.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import sisa.entity.*;
import sisa.repository.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the business rules unique to Module 8 (report FR-13): the double-booking
 * check (business rule 3), Student/Parent having no access at all (business rule 1),
 * and the approve/reject lifecycle's effect on that check.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ResourceBookingRepository resourceBookingRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private TimetableSlotRepository timetableSlotRepository;

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

    private Resource createResource(String name, ResourceType type, boolean autoApprove) {
        Resource resource = new Resource();
        resource.setName(name);
        resource.setType(type);
        resource.setAutoApprove(autoApprove);
        return resourceRepository.save(resource);
    }

    /**
     * Module 8 integration (report section 7, item 10; business rule 4): the reverse
     * direction of TimetableAssignmentServiceTest's equivalent case — a recurring
     * TimetableSlot already using this room blocks a new ad-hoc booking for the
     * matching day-of-week/period.
     */
    @Test
    void adHocBookingIsRejectedWhenARecurringTimetableSlotAlreadyUsesThatRoomOnTheMatchingDayOfWeek() throws Exception {
        createUser("T2699409", "teacher409", Role.TEACHER);
        User classTeacherUser = createUser("T2699410", "classteacher410", Role.TEACHER);
        Teacher classTeacher = new Teacher();
        classTeacher.setTeacherId("T2699410");
        classTeacher.setUser(classTeacherUser);
        teacherRepository.save(classTeacher);

        Resource room = createResource("Room 20", ResourceType.ROOM, false);

        LocalDate date = LocalDate.of(2026, 10, 5);
        TimetableSlot slot = new TimetableSlot();
        slot.setClassName("9A");
        slot.setSubject("Maths");
        slot.setTeacher(classTeacher);
        slot.setDayOfWeek(date.getDayOfWeek());
        slot.setPeriodNumber(4);
        slot.setRoom(room);
        timetableSlotRepository.save(slot);

        mockMvc.perform(post("/resources/book")
                        .with(user("teacher409").roles("TEACHER"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(room.getId()))
                        .param("bookingDate", date.toString())
                        .param("periodNumber", "4")
                        .param("purpose", "Ad-hoc science fair"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Maths"),
                        org.hamcrest.Matchers.containsString("9A"))));
    }

    @Test
    void bookingTheSameRoomForTheSameDateAndOverlappingPeriodTwiceIsRejectedNamingTheExistingBooking() throws Exception {
        createUser("T2699401", "teacherA401", Role.TEACHER);
        createUser("T2699402", "teacherB402", Role.TEACHER);
        Resource lab = createResource("Chemistry Lab", ResourceType.LAB, false);
        LocalDate date = LocalDate.of(2026, 10, 5);

        mockMvc.perform(post("/resources/book")
                        .with(user("teacherA401").roles("TEACHER"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(lab.getId()))
                        .param("bookingDate", date.toString())
                        .param("periodNumber", "3")
                        .param("purpose", "Grade 9 titration practical"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        // Second, different requester, same resource/date/period -> rejected naming the first booking.
        mockMvc.perform(post("/resources/book")
                        .with(user("teacherB402").roles("TEACHER"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(lab.getId()))
                        .param("bookingDate", date.toString())
                        .param("periodNumber", "3")
                        .param("purpose", "Grade 10 titration practical"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("T2699401"),
                        org.hamcrest.Matchers.containsString("titration practical"))));

        List<ResourceBooking> bookings = resourceBookingRepository.findByResource_IdAndBookingDateAndPeriodNumberAndStatusNot(
                lab.getId(), date, 3, BookingStatus.REJECTED);
        assertThat(bookings).hasSize(1); // the conflicting second request was never persisted
    }

    /**
     * System Functions doc, School Resources & Facilities Management: Student "Search
     * library resources; view lab schedules", "Book a study room, if allowed"; Parent
     * "View facility schedules and closures". Both can now view; Parent can never book;
     * Student can only book a resource explicitly flagged studentBookable.
     */
    @Test
    void studentAndParentCanViewButOnlyStudentBookableResourcesAreBookableByAStudentAndNeverByAParent() throws Exception {
        createUser("S2699403", "student403", Role.STUDENT);
        createUser("P2699403", "parent403", Role.PARENT);
        Resource room = createResource("Room 5", ResourceType.ROOM, false);
        Resource studyRoom = createResource("Study Room 1", ResourceType.ROOM, false);
        studyRoom.setStudentBookable(true);
        resourceRepository.save(studyRoom);

        mockMvc.perform(get("/resources/availability").with(user("student403").roles("STUDENT")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/resources/availability").with(user("parent403").roles("PARENT")))
                .andExpect(status().isOk());

        // Parent: view-only, booking is always forbidden.
        mockMvc.perform(post("/resources/book")
                        .with(user("parent403").roles("PARENT"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(studyRoom.getId()))
                        .param("bookingDate", LocalDate.now().toString())
                        .param("periodNumber", "1")
                        .param("purpose", "Should be blocked"))
                .andExpect(status().isForbidden());

        // Student: booking a non-studentBookable resource is rejected...
        mockMvc.perform(post("/resources/book")
                        .with(user("student403").roles("STUDENT"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(room.getId()))
                        .param("bookingDate", LocalDate.now().toString())
                        .param("periodNumber", "1")
                        .param("purpose", "Should be blocked"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        // ...but booking one flagged studentBookable succeeds.
        mockMvc.perform(post("/resources/book")
                        .with(user("student403").roles("STUDENT"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(studyRoom.getId()))
                        .param("bookingDate", LocalDate.now().toString())
                        .param("periodNumber", "2")
                        .param("purpose", "Group study"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void approvingARequestedBookingFlipsToApprovedAndItStillBlocksConflictingRequestsWhileRejectingUnblocks() throws Exception {
        createUser("PRINCIPAL2699", "principal2699", Role.PRINCIPAL);
        createUser("T2699404", "teacher404", Role.TEACHER);
        createUser("T2699405", "teacher405", Role.TEACHER);
        Resource room = createResource("Room 9", ResourceType.ROOM, false);
        LocalDate date = LocalDate.of(2026, 10, 6);

        mockMvc.perform(post("/resources/book")
                        .with(user("teacher404").roles("TEACHER"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(room.getId()))
                        .param("bookingDate", date.toString())
                        .param("periodNumber", "2")
                        .param("purpose", "Parent-teacher meeting"))
                .andExpect(status().is3xxRedirection());

        ResourceBooking requested = resourceBookingRepository
                .findByResource_IdAndBookingDateAndPeriodNumberAndStatusNot(room.getId(), date, 2, BookingStatus.REJECTED)
                .get(0);
        assertThat(requested.getStatus()).isEqualTo(BookingStatus.REQUESTED);

        mockMvc.perform(post("/principal/resources/approvals/{id}/approve", requested.getId())
                        .with(user("principal2699").roles("PRINCIPAL"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        ResourceBooking approved = resourceBookingRepository.findById(requested.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(BookingStatus.APPROVED);
        assertThat(approved.getDecidedBy()).isEqualTo("PRINCIPAL2699");

        // Still blocks another request for the exact same slot.
        mockMvc.perform(post("/resources/book")
                        .with(user("teacher405").roles("TEACHER"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(room.getId()))
                        .param("bookingDate", date.toString())
                        .param("periodNumber", "2")
                        .param("purpose", "Clashing request"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        // A separate booking, once rejected, blocks nothing.
        mockMvc.perform(post("/resources/book")
                        .with(user("teacher404").roles("TEACHER"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(room.getId()))
                        .param("bookingDate", date.toString())
                        .param("periodNumber", "5")
                        .param("purpose", "To be rejected"))
                .andExpect(status().is3xxRedirection());
        ResourceBooking toReject = resourceBookingRepository
                .findByResource_IdAndBookingDateAndPeriodNumberAndStatusNot(room.getId(), date, 5, BookingStatus.REJECTED)
                .get(0);

        mockMvc.perform(post("/principal/resources/approvals/{id}/reject", toReject.getId())
                        .with(user("principal2699").roles("PRINCIPAL"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(resourceBookingRepository.findById(toReject.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.REJECTED);

        mockMvc.perform(post("/resources/book")
                        .with(user("teacher405").roles("TEACHER"))
                        .with(csrf())
                        .param("resourceId", String.valueOf(room.getId()))
                        .param("bookingDate", date.toString())
                        .param("periodNumber", "5")
                        .param("purpose", "Now free to book"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
    }
}
