package sisa.service;

import sisa.entity.Teacher;
import sisa.entity.User;
import sisa.repository.TeacherRepository;
import sisa.service.dto.TeacherAssignmentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

@Service
public class TeacherManagementService {

    private final TeacherRepository teacherRepository;
    private final AuditLogService auditLogService;

    public TeacherManagementService(TeacherRepository teacherRepository, AuditLogService auditLogService) {
        this.teacherRepository = teacherRepository;
        this.auditLogService = auditLogService;
    }

    public List<Teacher> listAll() {
        return teacherRepository.findAll().stream()
                .sorted(Comparator.comparing(t -> t.getUser().getFullName()))
                .toList();
    }

    public Teacher getOrThrow(String teacherId) {
        return teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("No such teacher: " + teacherId));
    }

    @Transactional
    public void updateAssignment(String teacherId, TeacherAssignmentRequest req, User actingUser) {
        Teacher teacher = getOrThrow(teacherId);

        if (req.isClassTeacher() && !StringUtils.hasText(req.getAssignedClassName())) {
            throw new IllegalArgumentException("A Class Teacher needs a class to be assigned to.");
        }

        if (req.isClassTeacher() && StringUtils.hasText(req.getAssignedClassName())) {
            teacherRepository.findByAssignedClassNameAndClassTeacherTrue(req.getAssignedClassName()).stream()
                    .filter(other -> !other.getTeacherId().equals(teacherId))
                    .findFirst()
                    .ifPresent(other -> {
                        throw new IllegalArgumentException(other.getUser().getFullName() + " (" + other.getTeacherId()
                                + ") is already the Class Teacher for " + req.getAssignedClassName()
                                + " — remove that assignment first.");
                    });
        }

        teacher.setSubjectSpecialty(req.getSubjectSpecialty());
        teacher.setJoiningYear(req.getJoiningYear());
        teacher.setClassTeacher(req.isClassTeacher());
        teacher.setAssignedClassName(req.isClassTeacher() ? req.getAssignedClassName() : null);
        teacherRepository.save(teacher);

        auditLogService.log(teacherId, actingUser.getUserId(), "UPDATE_TEACHER_ASSIGNMENT",
                actingUser.getFullName() + " updated " + teacher.getUser().getFullName() + "'s subject/class assignment");
    }

    @Transactional
    public void updateSalary(String teacherId, Double monthlySalary) {
        Teacher teacher = getOrThrow(teacherId);
        teacher.setMonthlySalary(monthlySalary);
        teacherRepository.save(teacher);
    }
}
