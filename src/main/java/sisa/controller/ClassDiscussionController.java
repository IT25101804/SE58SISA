package sisa.controller;

import sisa.entity.ClassDiscussionPost;
import sisa.entity.Role;
import sisa.entity.Student;
import sisa.entity.Teacher;
import sisa.entity.User;
import sisa.repository.ClassDiscussionPostRepository;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/discussions")
public class ClassDiscussionController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ClassDiscussionPostRepository classDiscussionPostRepository;

    public ClassDiscussionController(UserRepository userRepository, StudentRepository studentRepository,
                                     TeacherRepository teacherRepository,
                                     ClassDiscussionPostRepository classDiscussionPostRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.classDiscussionPostRepository = classDiscussionPostRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    private String resolveClassName(User user) {
        if (user.getRole() == Role.STUDENT) {
            Student student = studentRepository.findById(user.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No student record found."));
            if (student.getClassName() == null || student.getClassName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You aren't assigned to a class yet.");
            }
            return student.getClassName();
        }
        if (user.getRole() == Role.TEACHER) {
            Teacher teacher = teacherRepository.findById(user.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No teacher record found."));
            if (!teacher.isClassTeacher() || teacher.getAssignedClassName() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a Class Teacher can open a class discussion board.");
            }
            return teacher.getAssignedClassName();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Class discussions are only for Students and their Class Teacher.");
    }

    @GetMapping
    public String view(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");

        String className = resolveClassName(user);
        model.addAttribute("className", className);
        model.addAttribute("posts", classDiscussionPostRepository.findByClassNameOrderByPostedAtAsc(className));
        return "comms/class-discussion";
    }

    @PostMapping
    public String post(@RequestParam String body, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        String className = resolveClassName(user);
        if (body == null || body.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Message can't be empty.");
            return "redirect:/discussions";
        }
        ClassDiscussionPost post = new ClassDiscussionPost();
        post.setClassName(className);
        post.setAuthorUserId(user.getUserId());
        post.setAuthorName(user.getFullName());
        post.setBody(body.trim());
        classDiscussionPostRepository.save(post);
        redirectAttributes.addFlashAttribute("success", "Posted.");
        return "redirect:/discussions";
    }
}
