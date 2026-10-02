package sisa.repository;

import sisa.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("select m from Message m where (m.fromUserId = :a and m.toUserId = :b) or (m.fromUserId = :b and m.toUserId = :a) "
            + "order by m.sentAt asc")
    List<Message> threadBetween(@Param("a") String a, @Param("b") String b);

    @Query("select m from Message m where m.fromUserId = :userId or m.toUserId = :userId order by m.sentAt desc")
    List<Message> findAllInvolving(@Param("userId") String userId);

    long countByToUserIdAndReadFalse(String toUserId);
}
