package sisa.repository;

import sisa.entity.AccountStatus;
import sisa.entity.Role;
import sisa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    List<User> findByRole(Role role);
    List<User> findByStatus(AccountStatus status);
    List<User> findTop5ByStatusOrderByCreatedAtDesc(AccountStatus status);
    long countByUserIdStartingWith(String prefix);
    long countByRoleInAndStatus(List<Role> roles, AccountStatus status);
}