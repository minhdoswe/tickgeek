package minhdoswe.tickgeek.module.email;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outboxes")
public class Outbox {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "outbox_status")
    private OutboxStatus status;

    @Column(name = "verification_code_id")
    private Long verificationCodeId;

    protected Outbox() {
    }

    public static Outbox forCode(Long verificationCodeId, String payload, Instant now) {
        Outbox job = new Outbox();
        job.verificationCodeId = verificationCodeId;
        job.payload = payload;
        job.createdAt = now;
        job.nextAttemptAt = now;
        job.status = OutboxStatus.PENDING;
        return job;
    }

    public UUID getId() {
        return id;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Long getVerificationCodeId() {
        return verificationCodeId;
    }
}
