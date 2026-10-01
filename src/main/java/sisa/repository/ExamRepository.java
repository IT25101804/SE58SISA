package sisa.repository;

import sisa.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByClassNameOrderByExamDateDesc(String className);
    List<Exam> findByCreatedBy_TeacherIdOrderByExamDateDesc(String teacherId);
    boolean existsByCreatedBy_TeacherId(String teacherId);
}
