package com.skillsphere.identity.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A client a person has signed in from.
 *
 * <p>Exists so a user can answer the question "where am I logged in, and can I
 * end that session". Showing someone their active devices is one of the few
 * security features that is genuinely usable by non-experts: an unfamiliar
 * entry is understandable in a way that a token expiry policy never is.
 */
@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
public class UserDevice extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_label", length = 150)
    private String deviceLabel;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean trusted = false;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    public void touch(String ip) {
        this.lastSeenAt = Instant.now();
        this.ipAddress = ip;
    }
}
