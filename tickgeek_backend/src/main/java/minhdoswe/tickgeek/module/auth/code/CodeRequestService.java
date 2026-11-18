package minhdoswe.tickgeek.module.auth.code;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import minhdoswe.tickgeek.common.EmailAddress;
import minhdoswe.tickgeek.config.AuthProperties;
import minhdoswe.tickgeek.module.auth.dto.CodeRequestResponse;
import minhdoswe.tickgeek.module.auth.limit.CodeRequestContext;
import minhdoswe.tickgeek.module.auth.limit.CodeRequestLimit;
import minhdoswe.tickgeek.module.auth.limit.LimitViolation;
import minhdoswe.tickgeek.module.auth.limit.implement.ResendCooldownLimit;
import minhdoswe.tickgeek.module.email.CodeEmailComposer;
import minhdoswe.tickgeek.module.email.CodeEmailPayload;
import minhdoswe.tickgeek.module.email.CodeEmailQueue;

/**
 * Issues a code for an email. It never looks up accounts, so the response cannot reveal whether
 * the email is registered (FR-004, research R7).
 */
@Service
public class CodeRequestService {

    private final CodeRequestLockRepository lock;
    private final List<CodeRequestLimit> limits;
    private final ResendCooldownLimit cooldown;
    private final VerificationCodeRepository codes;
    private final CodeEmailQueue emailQueue;
    private final CodeGenerator generator;
    private final CodeHasher hasher;
    private final CodeEmailComposer composer;
    private final Clock clock;
    private final AuthProperties properties;

    public CodeRequestService(CodeRequestLockRepository lock, List<CodeRequestLimit> limits,
            ResendCooldownLimit cooldown, VerificationCodeRepository codes, CodeEmailQueue emailQueue,
            CodeGenerator generator, CodeHasher hasher, CodeEmailComposer composer, Clock clock,
            AuthProperties properties) {
        this.lock = lock;
        this.limits = limits;
        this.cooldown = cooldown;
        this.codes = codes;
        this.emailQueue = emailQueue;
        this.generator = generator;
        this.hasher = hasher;
        this.composer = composer;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public CodeRequestResponse request(String email, String source) {
        String normalizedEmail = EmailAddress.normalize(email);
        lock.acquire(normalizedEmail);
        CodeRequestContext context = new CodeRequestContext(normalizedEmail, source, clock.instant());
        enforceLimits(context);
        codes.invalidateLive(context.email());
        String code = generator.next();
        VerificationCode saved = codes.save(VerificationCode.pending(context, hasher.hash(context.email(), code)));
        String payload = composer.toPayload(new CodeEmailPayload(context.email(), code));
        emailQueue.enqueue(saved.getId(), payload, context.now());
        return new CodeRequestResponse(
                context.email(), properties.codeValidMinutes(), cooldown.nextAllowedAt(context.now()));
    }

    private void enforceLimits(CodeRequestContext context) {
        limits.stream()
                .map(limit -> limit.check(context))
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(CodeRequestService::refuse);
    }

    private static void refuse(LimitViolation violation) {
        throw violation.disclosesWait()
                ? TooManyRequestsException.cooldown(violation.retryAfterSeconds())
                : TooManyRequestsException.generic();
    }
}
