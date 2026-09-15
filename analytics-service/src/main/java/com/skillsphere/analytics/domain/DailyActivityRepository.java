package com.skillsphere.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyActivityRepository extends JpaRepository<DailyActivity, DailyActivityId> {

    Optional<DailyActivity> findById_UserIdAndId_ActivityDate(Long userId, LocalDate activityDate);

    List<DailyActivity> findById_UserIdOrderById_ActivityDateDesc(Long userId);
}
