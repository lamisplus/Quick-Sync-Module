package org.lamisplus.modules.sync.repository;

import org.lamisplus.modules.sync.domain.QuickSyncHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuickSyncHistoryRepository extends JpaRepository<QuickSyncHistory, Long> {
}