package sisa.repository;

import sisa.entity.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {
    Optional<AssignmentSubmission> findByAssignment_IdAndStudent_StudentId(Long assignmentId, String studentId);
    List<AssignmentSubmission> findByAssignment_IdOrderByStudent_User_FullNameAsc(Long assignmentId);
    List<AssignmentSubmission> findByStudent_StudentId(String studentId);
    boolean existsByStudent_StudentId(String studentId);
}
