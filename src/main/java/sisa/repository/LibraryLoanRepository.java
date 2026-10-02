package sisa.repository;

import sisa.entity.LibraryLoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryLoanRepository extends JpaRepository<LibraryLoan, Long> {
    List<LibraryLoan> findByReturnedFalseOrderByDueDateAsc();
    List<LibraryLoan> findByStudent_StudentIdOrderByDueDateAsc(String studentId);
    List<LibraryLoan> findByStudent_StudentIdInAndReturnedFalseOrderByDueDateAsc(List<String> studentIds);
    boolean existsByStudent_StudentId(String studentId);
}
