package com.skillsphere.analytics.domain;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class DailyActivityId implements Serializable {

    private Long userId;
    private LocalDate activityDate;

    public DailyActivityId(Long userId, LocalDate activityDate) {
        this.userId = userId;
        this.activityDate = activityDate;
    }
}
