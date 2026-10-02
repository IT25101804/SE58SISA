package sisa.repository;

import sisa.entity.AttendanceCorrectionRequest;
import sisa.entity.CorrectionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceCorrectionRequestRepository extends JpaRepository<AttendanceCorrectionRequest, Long> {
    List<AttendanceCorrectionRequest> findByStatusOrderByRequestedAtDesc(CorrectionRequestStatus status);
    List<AttendanceCorrectionRequest> findByStatusAndAttendanceRecord_ClassNameOrderByRequestedAtDesc(
            CorrectionRequestStatus status, String className);
    List<AttendanceCorrectionRequest> findByRequestedByStudentIdOrderByRequestedAtDesc(String studentId);
}
