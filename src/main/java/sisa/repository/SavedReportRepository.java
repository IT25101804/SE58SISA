package sisa.repository;

import sisa.entity.SavedReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {
    List<SavedReport> findByOwnerUserIdOrderByNameAsc(String ownerUserId);
    void deleteByOwnerUserId(String ownerUserId);
}
