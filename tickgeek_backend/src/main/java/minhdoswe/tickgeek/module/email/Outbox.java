package minhdoswe.tickgeek.module.email;

import java.time.Duration;
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

    protected Outbox() {
    }

    public static Outbox pending(String payload, Instant now) {
        Outbox job = new Outbox();
        job.payload = payload;
        job.createdAt = now;
        job.nextAttemptAt = now;
        job.status = OutboxStatus.PENDING;
        return job;
    }

    /** Counts an attempt before it is sent (specs/002-sending-code/research.md R2). */
    public void claim() {
        if (isPending()) {
            attempts++;
        }
    }

    /** A provider accepted the email at {@code now}; the payload is erased. */
    public void succeed(Instant now) {
        if (!isPending()) {
            return;
        }
        status = OutboxStatus.SENT;
        publishedAt = now;
        payload = null;
    }

    /**
     * The attempt failed and retries are left: wait {@code baseDelay × 2^(attempts − 1)}, so 5,
     * 10, 20, 40 s with a 5 s base. The only place the backoff is computed
     * (specs/002-sending-code/spec.md FR-004; research.md R6).
     */
    public void retryLater(Instant now, String error, Duration baseDelay) {
        if (!isPending()) {
            return;
        }
        lastError = error;
        nextAttemptAt = now.plus(baseDelay.multipliedBy(1L << Math.max(attempts - 1, 0)));
    }

    /** The job will never be sent: recorded as failed with its reason, payload erased (FR-007). */
    public void giveUp(String error) {
        if (!isPending()) {
            return;
        }
        status = OutboxStatus.ERROR;
        lastError = error;
        payload = null;
    }

    /** The email is no longer wanted (its code was replaced); payload erased (research.md R5). */
    public void cancel() {
        if (!isPending()) {
            return;
        }
        status = OutboxStatus.CANCELLED;
        payload = null;
    }

    public boolean isPending() {
        return status == OutboxStatus.PENDING;
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

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
