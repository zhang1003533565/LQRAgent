package com.lqragent.backend.agents.learnerprofile.repository;

import com.lqragent.backend.agents.learnerprofile.entity.LearnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, Long> {

    Optional<LearnerProfile> findByUserId(Long userId);
}
