package sisa.repository;

import sisa.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherRepository extends JpaRepository<Teacher, String> {

    List<Teacher> findByAssignedClassNameAndClassTeacherTrue(String assignedClassName);
}
