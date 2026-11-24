package minhdoswe.tickgeek.module.auth.code;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import minhdoswe.tickgeek.module.auth.limit.CodeRequestContext;

@Entity
@Table(name = "verification_codes")
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "code_hmac", nullable = false)
    private String codeHmac;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "request_source", nullable = false)
    private String requestSource;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "outbox_id")
    private UUID outboxId;

    protected VerificationCode() {
    }

    public static VerificationCode pending(CodeRequestContext request, String codeHmac, UUID outboxId) {
        VerificationCode code = new VerificationCode();
        code.email = request.email();
        code.requestSource = request.source();
        code.createdAt = request.now();
        code.codeHmac = codeHmac;
        code.status = CodeStatus.PENDING;
        code.outboxId = outboxId;
        return code;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHmac() {
        return codeHmac;
    }

    public CodeStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getRequestSource() {
        return requestSource;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getOutboxId() {
        return outboxId;
    }
}
