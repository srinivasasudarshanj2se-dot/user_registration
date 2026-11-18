package org.example.user_registration.calls;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallRecordRepository extends JpaRepository<CallRecord, String> {
    List<CallRecord> findAllByOrderByStartedAtDesc(Pageable pageable);
}
