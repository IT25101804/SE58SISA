package sisa.service;

import sisa.entity.*;
import sisa.repository.*;
import sisa.entity.*;
import sisa.repository.*;
import sisa.service.dto.CreateAccountRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountFactory {

    private final UserRepository userRepository;
    private final RegistrarRepository registrarRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ParentRepository parentRepository;
    private final IdGeneratorService idGeneratorService;
    private final PasswordEncoder passwordEncoder;

    public UserAccountFactory(UserRepository userRepository,
                              RegistrarRepository registrarRepository,
                              StudentRepository studentRepository,
                              TeacherRepository teacherRepository,
                              ParentRepository parentRepository,
                              IdGeneratorService idGeneratorService,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.registrarRepository = registrarRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.parentRepository = parentRepository;
        this.idGeneratorService = idGeneratorService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createFor(Role role, CreateAccountRequest req) {
        String userId = switch (role) {
            case REGISTRAR -> idGeneratorService.nextRegistrarId();
            case STUDENT   -> idGeneratorService.nextStudentId();
            case TEACHER   -> idGeneratorService.nextTeacherId();
            case PARENT    -> idGeneratorService.nextParentId();
            case PRINCIPAL -> throw new IllegalArgumentException("The Principal account is seeded once and cannot be recreated.");
        };

        User user = new User();
        user.setUserId(userId);
        user.setUsername(userId);
        user.setPassword(passwordEncoder.encode(req.getRawPassword()));
        user.setFullName(req.getFullName());
        user.setEmail(req.getEmail());
        user.setRole(role);
        user.setDeletable(true);
        user.setStatus(role == Role.REGISTRAR ? AccountStatus.APPROVED : AccountStatus.PENDING);
        userRepository.save(user);

        switch (role) {
            case REGISTRAR -> {
                Registrar registrar = new Registrar();
                registrar.setRegistrarId(userId);
                registrar.setUser(user);
                registrarRepository.save(registrar);
            }
            case STUDENT -> {
                Student student = new Student();
                student.setStudentId(userId);
                student.setUser(user);
                studentRepository.save(student);
            }
            case TEACHER -> {
                Teacher teacher = new Teacher();
                teacher.setTeacherId(userId);
                teacher.setUser(user);
                teacherRepository.save(teacher);
            }
            case PARENT -> {
                Parent parent = new Parent();
                parent.setParentId(userId);
                parent.setUser(user);
                parentRepository.save(parent);
            }
            default -> { }
        }

        return user;
    }
}