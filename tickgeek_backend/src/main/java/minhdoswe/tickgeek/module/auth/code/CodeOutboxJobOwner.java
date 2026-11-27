package minhdoswe.tickgeek.module.auth.code;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import minhdoswe.tickgeek.config.AuthProperties;
import minhdoswe.tickgeek.module.email.OutboxJobOwner;

/**
 * Tells the email job about the code behind a code email. The code is found by its outbox and
 * changed only by its own id, never by email, so a job for an old code can't touch a newer one
 * (specs/002-sending-code/research.md R3, R4).
 */
@Component
public class CodeOutboxJobOwner implements OutboxJobOwner {

    private final VerificationCodeRepository codes;
    private final AuthProperties properties;

    public CodeOutboxJobOwner(VerificationCodeRepository codes, AuthProperties properties) {
        this.codes = codes;
        this.properties = properties;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isStillWanted(UUID outboxId) {
        return codes.findByOutboxId(outboxId)
                .map(code -> code.getStatus() == CodeStatus.PENDING)
                .orElse(true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delivered(UUID outboxId, Instant acceptedAt) {
        codes.findByOutboxId(outboxId).ifPresent(code ->
                codes.activatePending(code.getId(), acceptedAt, acceptedAt.plus(properties.codeTtl())));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void abandoned(UUID outboxId) {
        codes.findByOutboxId(outboxId).ifPresent(code -> codes.invalidatePending(code.getId()));
    }
}
