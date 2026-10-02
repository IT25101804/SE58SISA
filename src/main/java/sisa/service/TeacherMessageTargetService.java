package sisa.service;

import sisa.entity.*;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.TimetableSlotRepository;
import sisa.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Who a teacher's "New Message" goes to, chosen from a "Send To" list instead of typing a
 * user ID: the Principal, every student in a class they teach, one student, or one student's
 * parent. Students/parents are limited to classes the teacher actually teaches (their own
 * class as Class Teacher, plus every class on their timetable). Each resolved recipient gets
 * an ordinary one-to-one message through MessagingService, which still makes the final
 * role check.
 */
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

    /** The Class Teacher's own class plus every class on this teacher's timetable, A-Z. */
    public List<String> classesTaughtBy(String teacherUserId) {
        Teacher teacher = teacherRepository.findById(teacherUserId)
                .orElseThrow(() -> new IllegalStateException("No teacher record for " + teacherUserId));
        Set<String> classes = new TreeSet<>();
        if (teacher.isClassTeacher() && teacher.getAssignedClassName() != null) classes.add(teacher.getAssignedClassName());
        timetableSlotRepository.findByTeacher_TeacherIdOrderByPeriodNumberAsc(teacher.getTeacherId())
                .forEach(slot -> classes.add(slot.getClassName()));
        return new ArrayList<>(classes);
    }

    /** User IDs to message for the chosen target. */
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
