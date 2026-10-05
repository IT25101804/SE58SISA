// Handles academic overview for principal
package sisa.controller;

import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.ResultsAggregationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PrincipalAcademicController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ResultsAggregationService resultsAggregationService;

    public PrincipalAcademicController(UserRepository userRepository, StudentRepository studentRepository,
                                       ResultsAggregationService resultsAggregationService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.resultsAggregationService = resultsAggregationService;
    }

    @GetMapping("/principal/results-overview")
    public String overview(@RequestParam(required = false) String className, Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        model.addAttribute("allClassNames", studentRepository.distinctClassNames());
        model.addAttribute("className", className);
        if (className != null && !className.isBlank()) {
            model.addAttribute("classRankings", resultsAggregationService.classRankings(className));
        }
        model.addAttribute("passRatePerSubject", resultsAggregationService.passRatePerSubject());
        model.addAttribute("bestTeacherPerSubject", resultsAggregationService.bestTeacherPerSubject());
        model.addAttribute("extraHelp", resultsAggregationService.studentsNeedingExtraHelp());
        return "principal/results-overview";
    }
}
