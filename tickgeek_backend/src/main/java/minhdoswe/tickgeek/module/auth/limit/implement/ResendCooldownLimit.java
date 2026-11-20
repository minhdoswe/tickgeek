package minhdoswe.tickgeek.module.auth.limit.implement;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import minhdoswe.tickgeek.module.auth.limit.CodeRequestContext;
import minhdoswe.tickgeek.module.auth.limit.CodeRequestLimit;
import minhdoswe.tickgeek.module.auth.limit.LimitViolation;
import minhdoswe.tickgeek.module.auth.limit.RetryAfter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import minhdoswe.tickgeek.config.AuthProperties;
import minhdoswe.tickgeek.module.auth.code.VerificationCodeRepository;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResendCooldownLimit implements CodeRequestLimit {

    public static final String NAME = "RESEND_COOLDOWN";

    private final VerificationCodeRepository codes;
    private final Duration cooldown;

    public ResendCooldownLimit(VerificationCodeRepository codes, AuthProperties properties) {
        this.codes = codes;
        this.cooldown = properties.limits().resendCooldown();
    }

    @Override
    public Optional<LimitViolation> check(CodeRequestContext context) {
        return codes.findLatestCreatedAt(context.email())
                .map(latest -> latest.plus(cooldown))
                .filter(availableAt -> context.now().isBefore(availableAt))
                .map(availableAt -> new LimitViolation(NAME, RetryAfter.secondsUntil(context.now(), availableAt), true));
    }

    public Instant nextAllowedAt(Instant requestedAt) {
        return requestedAt.plus(cooldown);
    }
}
