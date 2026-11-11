package com.henrique.dashboard.repository;

import com.henrique.dashboard.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Optional<UserAccount> findByEmailIgnoreCaseAndEnabledTrue(String email);
    List<UserAccount> findAllByOrderByEmailAsc();
    boolean existsByEnabledTrue();
}
