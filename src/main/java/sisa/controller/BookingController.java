package sisa.controller;

import sisa.entity.ResourceType;
import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.BookingService;
import sisa.service.dto.BookingRequestForm;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Booking (report FR-13). Staff (Teacher/Registrar/Principal) can view and book any
 * resource. Student and Parent (System Functions doc: Student "Search library
 * resources; view lab schedules", "Book a study room, if allowed"; Parent "View
 * facility schedules and closures") can now reach /resources/** too — Parent is
 * view-only, and Student may only book resources flagged studentBookable.
 */
@Controller
@RequestMapping("/resources")
public class BookingController {

    private final UserRepository userRepository;
    private final BookingService bookingService;

    public BookingController(UserRepository userRepository, BookingService bookingService) {
        this.userRepository = userRepository;
        this.bookingService = bookingService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping("/availability")
    public String availability(@RequestParam(required = false) LocalDate date,
                                @RequestParam(required = false) ResourceType type,
                                Authentication authentication, Model model) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "resources");
        model.addAttribute("date", effectiveDate);
        model.addAttribute("type", type);
        model.addAttribute("resourceTypes", ResourceType.values());
        model.addAttribute("periods", PrincipalTimetableController.periodRange());
        model.addAttribute("grid", bookingService.availabilityGrid(effectiveDate, type));
        return "resources/availability";
    }

    @GetMapping("/book")
    public String bookForm(@RequestParam(required = false) Long resourceId,
                            @RequestParam(required = false) LocalDate date,
                            @RequestParam(required = false) Integer period,
                            Authentication authentication, Model model) {
        User user = currentUser(authentication);
        if (user.getRole() == Role.PARENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Parents can view facility schedules but can't book a resource.");
        }
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "resources");
        model.addAttribute("resources", user.getRole() == Role.STUDENT ? bookingService.studentBookableResources() : bookingService.allResources());
        model.addAttribute("periods", PrincipalTimetableController.periodRange());

        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        BookingRequestForm form = new BookingRequestForm();
        form.setResourceId(resourceId);
        form.setBookingDate(effectiveDate);
        if (period != null) form.setPeriodNumber(period);
        model.addAttribute("form", form);
        // Thymeleaf's th:field/BindStatus renders a bound LocalDate with a locale date
        // style (e.g. "9/12/26") which HTML5 <input type="date"> rejects — expose a plain
        // ISO string outside the bound object so the date input's th:value stays yyyy-MM-dd.
        model.addAttribute("bookingDateIso", effectiveDate.toString());
        return "resources/book";
    }

    @PostMapping("/book")
    public String submitBooking(@ModelAttribute("form") BookingRequestForm form, Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (user.getRole() == Role.PARENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Parents can view facility schedules but can't book a resource.");
        }
        if (user.getRole() == Role.STUDENT) {
            boolean allowed = bookingService.studentBookableResources().stream()
                    .anyMatch(r -> r.getId().equals(form.getResourceId()));
            if (!allowed) {
                redirectAttributes.addFlashAttribute("error", "That resource isn't open for students to book.");
                return "redirect:/resources/book";
            }
        }
        try {
            bookingService.requestBooking(form, user.getUserId());
            redirectAttributes.addFlashAttribute("success",
                    "Booking request for " + form.getBookingDate() + " period " + form.getPeriodNumber() + " submitted.");
            return "redirect:/resources/availability?date=" + form.getBookingDate();
        } catch (RuntimeException ex) {
            // Redirect back to the same form (not the grid) so the conflict message sits
            // right next to what the requester just typed, ready to be corrected.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addAttribute("resourceId", form.getResourceId());
            redirectAttributes.addAttribute("date", form.getBookingDate());
            redirectAttributes.addAttribute("period", form.getPeriodNumber());
            return "redirect:/resources/book";
        }
    }
}
