package sisa.service;

import sisa.entity.*;
import sisa.entity.Assignment;
import sisa.entity.AssignmentSubmission;
import sisa.entity.Student;
import sisa.entity.SubmissionStatus;
import sisa.repository.AssignmentSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Student assignment submission (report FR-10, TC-06, business rule 4): late
 * submissions are marked, never silently blocked, unless the teacher has explicitly
 * closed the assignment.
 */
@Service
public class SubmissionService {

    private final AssignmentSubmissionRepository submissionRepository;

    public SubmissionService(AssignmentSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    public Optional<AssignmentSubmission> submissionFor(Long assignmentId, String studentId) {
        return submissionRepository.findByAssignment_IdAndStudent_StudentId(assignmentId, studentId);
    }

    /**
     * Submits (or resubmits, replacing the previous answer) as of right now. Status is
     * computed fresh every time from submittedAt vs. the assignment's dueDate — TC-06:
     * anything submitted after the due date is automatically LATE, not blocked.
     */
    @Transactional
    public AssignmentSubmission submit(Assignment assignment, Student student, String content) {
        if (assignment.isSubmissionsClosed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The teacher has closed submissions for this assignment.");
        }
        if (!student.getClassName().equals(assignment.getClassName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This assignment isn't set for your class.");
        }

        AssignmentSubmission submission = submissionRepository
                .findByAssignment_IdAndStudent_StudentId(assignment.getId(), student.getStudentId())
                .orElseGet(AssignmentSubmission::new);

        LocalDateTime now = LocalDateTime.now();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setFileUrlOrText(content);
        submission.setSubmittedAt(now);
        submission.setStatus(now.toLocalDate().isAfter(assignment.getDueDate()) ? SubmissionStatus.LATE : SubmissionStatus.ON_TIME);
        return submissionRepository.save(submission);
    }
}
