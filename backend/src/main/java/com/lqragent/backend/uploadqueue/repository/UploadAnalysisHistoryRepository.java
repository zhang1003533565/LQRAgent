package com.lqragent.backend.uploadqueue.repository;

import com.lqragent.backend.uploadqueue.entity.UploadAnalysisHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadAnalysisHistoryRepository extends JpaRepository<UploadAnalysisHistory, Long> {
}
