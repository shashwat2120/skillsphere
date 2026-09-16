package com.skillsphere.identity.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A registered passkey (WebAuthn public-key credential).
 *
 * <p>{@link #publicKey} holds the COSE public key exactly as reported by the
 * authenticator at registration — verification of every later assertion
 * happens against this value, never against anything supplied by the client
 * on the authenticate call. {@link #signCount} is updated after each
 * successful assertion and is the mechanism WebAuthn uses to detect a cloned
 * authenticator: a signature counter that goes backwards means two devices
 * are presenting the same credential.
 */
@Entity
@Table(name = "webauthn_credentials")
@Getter
@Setter
@NoArgsConstructor
public class WebauthnCredential extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Base64url-encoded credential id, as returned by the authenticator. */
    @Column(name = "credential_id", nullable = false, unique = true, length = 500)
    private String credentialId;

    /**
     * The COSE public key, CBOR-encoded exactly as webauthn4j serialises it.
     * Plain {@code byte[]} without {@code @Lob} — Hibernate 6 on PostgreSQL
     * already maps that to {@code bytea}, matching the V1 migration's column
     * type; {@code @Lob} here would ask for large-object (OID) storage
     * instead and fail schema validation at startup.
     */
    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(length = 100)
    private String transports;

    @Column(name = "device_label", length = 150)
    private String deviceLabel;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public WebauthnCredential(Long userId, String credentialId, byte[] publicKey,
                               long signCount, String transports, String deviceLabel) {
        this.userId = userId;
        this.credentialId = credentialId;
        this.publicKey = publicKey;
        this.signCount = signCount;
        this.transports = transports;
        this.deviceLabel = deviceLabel;
        this.createdAt = Instant.now();
    }
}
