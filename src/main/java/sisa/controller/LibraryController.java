package sisa.controller;

import sisa.entity.LibraryItem;
import sisa.entity.LibraryLoan;
import sisa.entity.Student;
import sisa.entity.User;
import sisa.repository.LibraryItemRepository;
import sisa.repository.LibraryLoanRepository;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * School Resources & Facilities Management module (System Functions doc, Registrar:
 * library catalog + loan upkeep). Already scoped to PRINCIPAL/REGISTRAR by
 * SecurityConfig's /registrar/** rule.
 */
@Controller
@RequestMapping("/registrar/library")
public class LibraryController {

    private final UserRepository userRepository;
    private final LibraryItemRepository libraryItemRepository;
    private final LibraryLoanRepository libraryLoanRepository;
    private final StudentRepository studentRepository;

    public LibraryController(UserRepository userRepository, LibraryItemRepository libraryItemRepository,
                             LibraryLoanRepository libraryLoanRepository, StudentRepository studentRepository) {
        this.userRepository = userRepository;
        this.libraryItemRepository = libraryItemRepository;
        this.libraryLoanRepository = libraryLoanRepository;
        this.studentRepository = studentRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String view(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "library");
        model.addAttribute("items", libraryItemRepository.findAllByOrderByTitleAsc());
        model.addAttribute("activeLoans", libraryLoanRepository.findByReturnedFalseOrderByDueDateAsc());
        return "registrar/library";
    }

    @PostMapping("/items")
    public String addItem(@RequestParam String title, @RequestParam(required = false) String author,
                          @RequestParam(required = false) String category,
                          @RequestParam(defaultValue = "1") int totalCopies,
                          RedirectAttributes redirectAttributes) {
        if (title == null || title.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Title can't be empty.");
            return "redirect:/registrar/library";
        }
        LibraryItem item = new LibraryItem();
        item.setTitle(title.trim());
        item.setAuthor(author != null ? author.trim() : null);
        item.setCategory(category != null ? category.trim() : null);
        int copies = Math.max(1, totalCopies);
        item.setTotalCopies(copies);
        item.setAvailableCopies(copies);
        libraryItemRepository.save(item);
        redirectAttributes.addFlashAttribute("success", "\"" + item.getTitle() + "\" added to the catalog.");
        return "redirect:/registrar/library";
    }

    @PostMapping("/loans")
    @Transactional
    public String issueLoan(@RequestParam Long libraryItemId, @RequestParam String studentId,
                            @RequestParam String dueDate, RedirectAttributes redirectAttributes) {
        LibraryItem item = libraryItemRepository.findById(libraryItemId).orElse(null);
        Student student = studentRepository.findById(studentId).orElse(null);
        if (item == null || student == null) {
            redirectAttributes.addFlashAttribute("error", "Unknown item or student.");
            return "redirect:/registrar/library";
        }
        if (item.getAvailableCopies() <= 0) {
            redirectAttributes.addFlashAttribute("error", "No copies of \"" + item.getTitle() + "\" are available right now.");
            return "redirect:/registrar/library";
        }
        LibraryLoan loan = new LibraryLoan();
        loan.setLibraryItem(item);
        loan.setStudent(student);
        loan.setBorrowedAt(LocalDate.now());
        loan.setDueDate(LocalDate.parse(dueDate));
        libraryLoanRepository.save(loan);

        item.setAvailableCopies(item.getAvailableCopies() - 1);
        libraryItemRepository.save(item);

        redirectAttributes.addFlashAttribute("success", "\"" + item.getTitle() + "\" issued to " + student.getStudentId() + ".");
        return "redirect:/registrar/library";
    }

    @PostMapping("/loans/{id}/return")
    @Transactional
    public String returnLoan(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        LibraryLoan loan = libraryLoanRepository.findById(id).orElse(null);
        if (loan == null) {
            redirectAttributes.addFlashAttribute("error", "No such loan.");
            return "redirect:/registrar/library";
        }
        if (!loan.isReturned()) {
            loan.setReturned(true);
            loan.setReturnedAt(LocalDate.now());
            libraryLoanRepository.save(loan);

            LibraryItem item = loan.getLibraryItem();
            item.setAvailableCopies(item.getAvailableCopies() + 1);
            libraryItemRepository.save(item);
        }
        redirectAttributes.addFlashAttribute("success", "Loan marked returned.");
        return "redirect:/registrar/library";
    }
}
