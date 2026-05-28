package com.lqragent.backend.agents.resource_generation.repository;

import com.lqragent.backend.agents.resource_generation.entity.ResourceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceItemRepository extends JpaRepository<ResourceItem, Long> {

    List<ResourceItem> findByKpId(String kpId);

    List<ResourceItem> findByKpIdAndResourceType(String kpId, String resourceType);

    List<ResourceItem> findByResourceType(String resourceType);
}
