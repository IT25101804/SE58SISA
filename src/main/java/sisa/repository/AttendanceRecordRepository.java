package sisa.repository;

import sisa.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByStudent_StudentIdAndAttendanceDate(String studentId, LocalDate attendanceDate);
    List<AttendanceRecord> findByClassNameAndAttendanceDate(String className, LocalDate attendanceDate);
    List<AttendanceRecord> findByStudent_StudentIdOrderByAttendanceDateDesc(String studentId);
    List<AttendanceRecord> findByAttendanceDate(LocalDate attendanceDate);
    boolean existsByStudent_StudentId(String studentId);
}
