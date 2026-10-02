package sisa.controller;

import sisa.entity.Student;
import sisa.entity.User;
import sisa.repository.LibraryLoanRepository;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ParentLibraryController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final LibraryLoanRepository libraryLoanRepository;

    public ParentLibraryController(UserRepository userRepository, StudentRepository studentRepository,
                                   LibraryLoanRepository libraryLoanRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.libraryLoanRepository = libraryLoanRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping("/parent/library-due-dates")
    public String dueDates(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "library");

        List<Student> children = studentRepository.findByParent_UserId(user.getUserId());
        model.addAttribute("children", children);

        List<String> childIds = children.stream().map(Student::getStudentId).toList();
        model.addAttribute("loans", childIds.isEmpty()
                ? List.of()
                : libraryLoanRepository.findByStudent_StudentIdInAndReturnedFalseOrderByDueDateAsc(childIds));
        return "parent/library-due-dates";
    }
}
