package sisa.report;

import sisa.entity.AccountStatus;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportingAggregationServiceTest {

    private ReportingAggregationService serviceWithNoDownstreamModules() {
        StudentRepository studentRepository = mock(StudentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TeacherRepository teacherRepository = mock(TeacherRepository.class);
        when(studentRepository.countByStatus(any())).thenReturn(5L);
        when(studentRepository.findAll()).thenReturn(List.of());
        when(studentRepository.search(any(), any())).thenReturn(List.of());
        when(userRepository.findByStatus(AccountStatus.PENDING)).thenReturn(List.of());
        when(teacherRepository.findAll()).thenReturn(List.of());

        return new ReportingAggregationService(studentRepository, userRepository, teacherRepository,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void dashboardStatsDegradeGracefullyWhenDownstreamRepositoriesAreAbsent() {
        ReportingAggregationService service = serviceWithNoDownstreamModules();

        assertThat(service.totalEnrolledStudents()).isEqualTo(5);
        assertThat(service.pendingApprovalsCount()).isZero();
        assertThat(service.todaysAttendancePercentageOrNull()).isNull();
        assertThat(service.roomsBookedTodayOrNull()).isNull();
    }

    @Test
    void attendanceAndAcademicReportsDegradeToAnEmptyReportInsteadOfThrowing() {
        ReportingAggregationService service = serviceWithNoDownstreamModules();

        ReportingAggregationService.Report attendance = service.build(ReportType.ATTENDANCE, null, null, null, true);
        assertThat(attendance.data().rows()).isEmpty();
        assertThat(attendance.chart()).isNull();

        ReportingAggregationService.Report academic = service.build(ReportType.ACADEMIC, null, null, null, true);
        assertThat(academic.data().rows()).isEmpty();
        assertThat(academic.chart()).isNull();
    }

    @Test
    void reportsThatDontDependOnTheMissingModulesStillWorkNormally() {
        ReportingAggregationService service = serviceWithNoDownstreamModules();

        ReportingAggregationService.Report staff = service.build(ReportType.STAFF, null, null, null, true);
        assertThat(staff.data()).isNotNull();
        assertThat(staff.data().rows()).isEmpty();

        ReportingAggregationService.Report enrolment = service.build(ReportType.ENROLMENT, null, null, null, true);
        assertThat(enrolment.data()).isNotNull();
    }
}
