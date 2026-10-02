package sisa.repository;

import sisa.entity.Student;
import sisa.entity.StudentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, String> {

    List<Student> findTop5ByOrderByEnrollmentDateDesc();

    List<Student> findByParent_UserId(String parentUserId);

    long countByStatus(StudentStatus status);

    long countByClassNameIgnoreCaseAndStatus(String className, StudentStatus status);

    List<Student> findByClassNameAndStatusOrderByUser_FullNameAsc(String className, StudentStatus status);

    @Query("select distinct s.className from Student s where s.className is not null and s.className <> '' order by s.className")
    List<String> distinctClassNames();

    @Query("select s from Student s where "
            + "(:q is null or lower(s.user.fullName) like lower(concat('%', :q, '%')) "
            + "          or lower(s.studentId) like lower(concat('%', :q, '%')) "
            + "          or lower(s.className) like lower(concat('%', :q, '%'))) "
            + "and (:status is null or s.status = :status) "
            + "order by s.user.fullName asc")
    List<Student> search(@Param("q") String q, @Param("status") StudentStatus status);

    boolean existsByParent_UserId(String parentUserId);
}
