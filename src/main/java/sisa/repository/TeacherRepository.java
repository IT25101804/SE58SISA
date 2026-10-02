package sisa.repository;

import sisa.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherRepository extends JpaRepository<Teacher, String> {

    /** Used to keep at most one Class Teacher per class when assigning teachers to classes/subjects. */
    List<Teacher> findByAssignedClassNameAndClassTeacherTrue(String assignedClassName);
}
