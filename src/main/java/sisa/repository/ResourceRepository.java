package sisa.repository;

import sisa.entity.Resource;
import sisa.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findByTypeOrderByNameAsc(ResourceType type);
    List<Resource> findAllByOrderByTypeAscNameAsc();

    /** Looks up a class/room by exact type + name — used to match a Student.className against a CLASSROOM resource. */
    Optional<Resource> findByTypeAndNameIgnoreCase(ResourceType type, String name);
}
