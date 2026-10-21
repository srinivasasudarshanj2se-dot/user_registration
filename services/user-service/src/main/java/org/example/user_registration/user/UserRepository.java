package org.example.user_registration.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUuid(String uuid);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
