package com.gridapp.repository;

import com.gridapp.model.entity.ClaimHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimHistoryRepository extends JpaRepository<ClaimHistory, Long> {
    // Append-only audit log — no complex queries needed at this stage
}
