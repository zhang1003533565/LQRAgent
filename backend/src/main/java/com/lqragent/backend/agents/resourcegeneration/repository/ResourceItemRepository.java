package com.lqragent.backend.agents.content.summary.lessongeneration.repository;

import com.lqragent.backend.agents.content.summary.lessongeneration.entity.ResourceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ResourceItemRepository extends JpaRepository<ResourceItem, Long> {

    List<ResourceItem> findByKpId(String kpId);

    List<ResourceItem> findByKpIdAndResourceType(String kpId, String resourceType);

    List<ResourceItem> findByResourceType(String resourceType);

    List<ResourceItem> findBySubject(String subject);

    @Query("SELECT DISTINCT r.subject FROM ResourceItem r WHERE r.subject IS NOT NULL ORDER BY r.subject")
    List<String> findDistinctSubjects();
}
