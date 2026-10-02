package sisa.controller;

import sisa.entity.User;
import sisa.repository.LibraryItemRepository;
import sisa.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * School Resources & Facilities Management module (System Functions doc, Student:
 * "Search library resources"). Read-only. Already scoped to PRINCIPAL/STUDENT by
 * SecurityConfig's /student/** rule.
 */
@Controller
public class StudentLibraryController {

    private final UserRepository userRepository;
    private final LibraryItemRepository libraryItemRepository;

    public StudentLibraryController(UserRepository userRepository, LibraryItemRepository libraryItemRepository) {
        this.userRepository = userRepository;
        this.libraryItemRepository = libraryItemRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping("/student/library")
    public String search(@RequestParam(required = false) String q, Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "library");
        model.addAttribute("q", q);
        if (StringUtils.hasText(q)) {
            model.addAttribute("items", libraryItemRepository
                    .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrCategoryContainingIgnoreCase(q.trim(), q.trim(), q.trim()));
        } else {
            model.addAttribute("items", libraryItemRepository.findAllByOrderByTitleAsc());
        }
        return "student/library";
    }
}
