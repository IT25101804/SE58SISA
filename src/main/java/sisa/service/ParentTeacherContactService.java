package sisa.service;

import sisa.entity.AccountStatus;
import sisa.entity.Student;
import sisa.entity.Teacher;
import sisa.entity.TimetableSlot;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.TimetableSlotRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The teachers a parent can message, for the "New Message" form: every teacher who
 * teaches one of the parent's children (from that class's timetable, with the subjects
 * they teach) plus the child's Class Teacher — so a parent picks "Mr. Perera — Maths"
 * instead of having to know a T-number. MessagingService still makes the final
 * PARENT -> TEACHER permission check when the message is sent.
 */
@Service
public class ParentTeacherContactService {

    /** One dropdown option: who (userId to send to), their name, what they teach this child, and which child. */
    public record TeacherContact(String userId, String teacherName, String subjects, String childName, String className) {}

    private final StudentRepository studentRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final TeacherRepository teacherRepository;

    public ParentTeacherContactService(StudentRepository studentRepository, TimetableSlotRepository timetableSlotRepository,
                                       TeacherRepository teacherRepository) {
        this.studentRepository = studentRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.teacherRepository = teacherRepository;
    }

    public List<TeacherContact> teachersForParent(String parentUserId) {
        List<TeacherContact> contacts = new ArrayList<>();
        for (Student child : studentRepository.findByParent_UserId(parentUserId)) {
            String className = child.getClassName();
            if (className == null) continue;

            // teacherId -> (teacher, subjects they teach in this class), in timetable order
            Map<String, Teacher> teachers = new LinkedHashMap<>();
            Map<String, Set<String>> subjects = new LinkedHashMap<>();
            for (Teacher classTeacher : teacherRepository.findByAssignedClassNameAndClassTeacherTrue(className)) {
                teachers.put(classTeacher.getTeacherId(), classTeacher);
                subjects.computeIfAbsent(classTeacher.getTeacherId(), k -> new LinkedHashSet<>()).add("Class Teacher");
            }
            for (TimetableSlot slot : timetableSlotRepository.findByClassNameOrderByPeriodNumberAsc(className)) {
                Teacher t = slot.getTeacher();
                teachers.putIfAbsent(t.getTeacherId(), t);
                subjects.computeIfAbsent(t.getTeacherId(), k -> new LinkedHashSet<>()).add(slot.getSubject());
            }

            String childName = child.getUser() != null ? child.getUser().getFullName() : child.getStudentId();
            teachers.values().stream()
                    .filter(t -> t.getUser() != null && t.getUser().getStatus() == AccountStatus.APPROVED)
                    .sorted(Comparator.comparing(t -> t.getUser().getFullName(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(t -> contacts.add(new TeacherContact(t.getUser().getUserId(), t.getUser().getFullName(),
                            String.join(", ", subjects.get(t.getTeacherId())), childName, className)));
        }
        return contacts;
    }
}
