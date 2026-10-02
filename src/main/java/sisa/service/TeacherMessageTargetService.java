package sisa.service;

import sisa.entity.*;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.TimetableSlotRepository;
import sisa.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TeacherMessageTargetService {

    private final TeacherRepository teacherRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public TeacherMessageTargetService(TeacherRepository teacherRepository, TimetableSlotRepository timetableSlotRepository,
                                       StudentRepository studentRepository, UserRepository userRepository) {
        this.teacherRepository = teacherRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    public List<String> classesTaughtBy(String teacherUserId) {
        Teacher teacher = teacherRepository.findById(teacherUserId)
                .orElseThrow(() -> new IllegalStateException("No teacher record for " + teacherUserId));
        Set<String> classes = new TreeSet<>();
        if (teacher.isClassTeacher() && teacher.getAssignedClassName() != null) classes.add(teacher.getAssignedClassName());
        timetableSlotRepository.findByTeacher_TeacherIdOrderByPeriodNumberAsc(teacher.getTeacherId())
                .forEach(slot -> classes.add(slot.getClassName()));
        return new ArrayList<>(classes);
    }

    public List<String> resolve(User teacher, String sendTo, String className, String studentId) {
        if (sendTo == null || sendTo.isBlank()) {
            throw new IllegalArgumentException("Choose who to send this to.");
        }
        List<String> taught = classesTaughtBy(teacher.getUserId());
        return switch (sendTo) {
            case "PRINCIPAL" -> {
                List<String> principals = userRepository.findByRole(Role.PRINCIPAL).stream()
                        .filter(u -> u.getStatus() == AccountStatus.APPROVED)
                        .map(User::getUserId)
                        .toList();
                if (principals.isEmpty()) throw new IllegalArgumentException("There is no active Principal account.");
                yield principals;
            }
            case "CLASS" -> {
                if (className == null || className.isBlank()) throw new IllegalArgumentException("Choose a class.");
                if (!taught.contains(className)) throw new IllegalArgumentException("You don't teach " + className + ".");
                List<String> students = studentRepository.findByClassNameAndStatusOrderByUser_FullNameAsc(className, StudentStatus.ACTIVE)
                        .stream().map(Student::getStudentId).toList();
                if (students.isEmpty()) throw new IllegalArgumentException(className + " has no active students.");
                yield students;
            }
            case "STUDENT" -> List.of(requireTaughtStudent(studentId, taught).getStudentId());
            case "PARENT" -> {
                Student student = requireTaughtStudent(studentId, taught);
                if (student.getParent() == null) {
                    throw new IllegalArgumentException("Student " + student.getStudentId() + " has no linked parent.");
                }
                yield List.of(student.getParent().getUserId());
            }
            default -> throw new IllegalArgumentException("Unknown recipient type: " + sendTo);
        };
    }

    private Student requireTaughtStudent(String studentId, List<String> taught) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Type the student's ID (e.g. S2600001).");
        }
        Student student = studentRepository.findById(studentId.trim())
                .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId.trim()));
        if (!taught.contains(student.getClassName())) {
            throw new IllegalArgumentException("Student " + student.getStudentId() + " isn't in a class you teach.");
        }
        return student;
    }
}
