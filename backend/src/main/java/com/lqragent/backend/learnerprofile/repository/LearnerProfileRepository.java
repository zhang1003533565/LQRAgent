package com.lqragent.backend.learnerprofile.repository;

import com.lqragent.backend.learnerprofile.entity.LearnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, Long> {

    Optional<LearnerProfile> findByUserId(Long userId);
}
