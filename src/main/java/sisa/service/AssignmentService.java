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

/** Learning material / homework (report FR-10, business rules 3 & 5). */
@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;

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

    /** Grades one student's submission — only the Teacher who set the assignment may (business rule 5). */
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

    private Assignment requireOwnedBy(Long assignmentId, Teacher teacher) {
        Assignment assignment = getOrThrow(assignmentId);
        if (!assignment.getTeacher().getTeacherId().equals(teacher.getTeacherId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the teacher who set this assignment can manage it.");
        }
        return assignment;
    }
}
