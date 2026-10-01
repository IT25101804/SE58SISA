package sisa.repository;

import sisa.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
