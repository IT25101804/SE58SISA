package sisa.repository;

import sisa.entity.Mark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarkRepository extends JpaRepository<Mark, Long> {
    Optional<Mark> findByExam_IdAndStudent_StudentId(Long examId, String studentId);
    List<Mark> findByExam_Id(Long examId);
    List<Mark> findByStudent_StudentIdOrderByExam_ExamDateDesc(String studentId);
    boolean existsByStudent_StudentId(String studentId);
}
