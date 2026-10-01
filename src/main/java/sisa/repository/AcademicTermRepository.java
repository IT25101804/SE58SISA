package sisa.repository;

import sisa.entity.AcademicTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {
    List<AcademicTerm> findAllByOrderByStartDateDesc();
    Optional<AcademicTerm> findByCurrentTrue();
    List<AcademicTerm> findAllByCurrentTrue();
}
