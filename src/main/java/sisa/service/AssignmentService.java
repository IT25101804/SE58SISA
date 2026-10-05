package sisa.service;

import sisa.entity.*;
import sisa.entity.Assignment;
import sisa.entity.AssignmentSubmission;
import sisa.entity.Teacher;
import sisa.repository.AssignmentRepository;
import sisa.repository.AssignmentSubmissionRepository;
import sisa.service.dto.AssignmentForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssignmentService { // Act As Facade

    private final AssignmentRepository assignmentRepository; //Subsystem 1
    private final AssignmentSubmissionRepository submissionRepository; // Subsystem 2

    public AssignmentService(AssignmentRepository assignmentRepository, AssignmentSubmissionRepository submissionRepository) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public Assignment create(AssignmentForm form, Teacher teacher) {
        Assignment assignment = new Assignment();
        assignment.setTeacher(teacher);
        assignment.setClassName(form.getClassName());
        assignment.setSubject(form.getSubject());
        assignment.setTitle(form.getTitle());
        assignment.setDescription(form.getDescription());
        assignment.setMaterialUrl(form.getMaterialUrl());
        assignment.setDueDate(LocalDate.parse(form.getDueDate()));
        assignment.setCreatedAt(LocalDateTime.now());
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public Assignment update(Long assignmentId, AssignmentForm form, Teacher actingTeacher) {
        Assignment assignment = requireOwnedBy(assignmentId, actingTeacher);
        assignment.setClassName(form.getClassName());
        assignment.setTitle(form.getTitle());
        assignment.setDescription(form.getDescription());
        assignment.setMaterialUrl(form.getMaterialUrl());
        assignment.setDueDate(LocalDate.parse(form.getDueDate()));
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public void delete(Long assignmentId, Teacher actingTeacher) {
        Assignment assignment = requireOwnedBy(assignmentId, actingTeacher);
        submissionRepository.deleteAll(submissionRepository.findByAssignment_IdOrderByStudent_User_FullNameAsc(assignmentId));
        assignmentRepository.delete(assignment);
    }

    public Assignment getOwned(Long assignmentId, Teacher teacher) {
        return requireOwnedBy(assignmentId, teacher);
    }

    public List<Assignment> listForTeacher(String teacherId) {
        return assignmentRepository.findByTeacher_TeacherIdOrderByDueDateDesc(teacherId);
    }

    public List<Assignment> listForClass(String className) {
        return assignmentRepository.findByClassNameOrderByDueDateAsc(className);
    }

    public Assignment getOrThrow(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such assignment: " + id));
    }

    public List<AssignmentSubmission> submissionsFor(Long assignmentId) {
        return submissionRepository.findByAssignment_IdOrderByStudent_User_FullNameAsc(assignmentId);
    }

    @Transactional
    public void setSubmissionsClosed(Long assignmentId, boolean closed, Teacher actingTeacher) {
        Assignment assignment = requireOwnedBy(assignmentId, actingTeacher);
        assignment.setSubmissionsClosed(closed);
        assignmentRepository.save(assignment);
    }

    @Transactional
    public void grade(Long assignmentId, Long submissionId, String grade, String feedback, Teacher actingTeacher) {
        requireOwnedBy(assignmentId, actingTeacher);
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("No such submission: " + submissionId));
        if (!submission.getAssignment().getId().equals(assignmentId)) {
            throw new IllegalArgumentException("That submission doesn't belong to this assignment.");
        }
        submission.setGrade(grade);
        submission.setFeedback(feedback);
        submission.setGradedBy(actingTeacher.getTeacherId());
        submission.setGradedAt(LocalDateTime.now());
        submissionRepository.save(submission);
    }

    @Transactional
    public void gradeWithMarks(Long assignmentId, Long submissionId, Double marks, String feedback, Teacher actingTeacher) {
        if (marks == null || marks < 0 || marks > 100) {
            throw new IllegalArgumentException("Enter marks between 0 and 100.");
        }
        requireOwnedBy(assignmentId, actingTeacher);
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("No such submission: " + submissionId));
        if (!submission.getAssignment().getId().equals(assignmentId)) {
            throw new IllegalArgumentException("That submission doesn't belong to this assignment.");
        }
        submission.setMarks(marks);
        submission.setGrade(MarksEntryService.gradeFor(marks, 100).name());
        submission.setFeedback(feedback);
        submission.setGradedBy(actingTeacher.getTeacherId());
        submission.setGradedAt(LocalDateTime.now());
        submissionRepository.save(submission);
    }

    private Assignment requireOwnedBy(Long assignmentId, Teacher teacher) {
        Assignment assignment = getOrThrow(assignmentId);
        if (!assignment.getTeacher().getTeacherId().equals(teacher.getTeacherId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the teacher who set this assignment can manage it.");
        }
        return assignment;
    }
}
