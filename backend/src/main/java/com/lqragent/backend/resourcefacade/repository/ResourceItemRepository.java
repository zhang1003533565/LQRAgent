package com.lqragent.backend.resourcefacade.repository;

import com.lqragent.backend.resourcefacade.entity.ResourceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceItemRepository extends JpaRepository<ResourceItem, Long> {

    List<ResourceItem> findByKpId(String kpId);

    List<ResourceItem> findByKpIdAndResourceType(String kpId, String resourceType);

    List<ResourceItem> findByResourceType(String resourceType);
}
