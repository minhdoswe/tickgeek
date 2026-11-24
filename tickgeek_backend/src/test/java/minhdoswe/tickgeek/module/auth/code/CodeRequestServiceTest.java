package minhdoswe.tickgeek.module.auth.code;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;

import minhdoswe.tickgeek.module.auth.dto.CodeRequestResponse;
import minhdoswe.tickgeek.module.auth.limit.CodeRequestContext;
import minhdoswe.tickgeek.module.auth.limit.CodeRequestLimit;
import minhdoswe.tickgeek.module.auth.limit.LimitViolation;
import minhdoswe.tickgeek.module.auth.limit.implement.ResendCooldownLimit;
import minhdoswe.tickgeek.module.email.CodeEmailComposer;
import minhdoswe.tickgeek.module.email.CodeEmailPayload;
import minhdoswe.tickgeek.module.email.CodeEmailQueue;
import minhdoswe.tickgeek.support.TestProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final String SOURCE = "1.2.3.4";
    private static final UUID OUTBOX_ID = UUID.fromString("019a7a2c-0000-7000-8000-000000000009");

    private final CodeRequestLockRepository lock = mock(CodeRequestLockRepository.class);
    private final CodeRequestLimit limit = mock(CodeRequestLimit.class);
    private final VerificationCodeRepository codes = mock(VerificationCodeRepository.class);
    private final CodeEmailQueue emailQueue = mock(CodeEmailQueue.class);
    private final CodeGenerator generator = mock(CodeGenerator.class);
    private final CodeEmailComposer composer = mock(CodeEmailComposer.class);
    private final CodeHasher hasher = new CodeHasher(TestProperties.auth());
    private CodeRequestService service;

    @BeforeEach
    void setUp() {
        ResendCooldownLimit cooldown = new ResendCooldownLimit(codes, TestProperties.auth());
        service = new CodeRequestService(lock, List.of(limit), cooldown, codes, emailQueue, generator, hasher,
                composer, Clock.fixed(NOW, ZoneOffset.UTC), TestProperties.auth());
        when(limit.check(any())).thenReturn(Optional.empty());
        when(generator.next()).thenReturn("042317");
        when(codes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(composer.toPayload(any())).thenReturn("{payload}");
    }

    /**
     * Proves a code request runs its steps in a safe order and stores only the hash, so parallel
     * requests can't slip past the limits, an older code stops working once a new one is sent,
     * and a database leak reveals no codes.
     * <p>
     * Cases: "  Ana@Example.com " → response for "ana@example.com" with 10 minutes and resend at
     * +60 s; lock → limits → invalidate older codes → queue the email with the plain code →
     * save a PENDING code with only its HMAC, pointing to the queued outbox. The outbox is saved
     * first because the code references it (specs/001-request-code/research.md R3, R5, R9;
     * specs/001-request-code/spec.md FR-006).
     */
    @Test
    void issuesAPendingHashedCodeAndQueuesTheEmail() {
        // Given: queuing the email returns the outbox id
        when(emailQueue.enqueue(any(), any())).thenReturn(OUTBOX_ID);

        // When: a code is requested for an email with spaces and capitals (no limit is hit, see setUp)
        CodeRequestResponse response = service.request("  Ana@Example.com ", SOURCE);

        // Then: the response has the normalized email, 10 minutes and the 60-second cooldown
        assertThat(response).isEqualTo(new CodeRequestResponse("ana@example.com", 10, NOW.plusSeconds(60)));
        // Then: lock, limits, invalidate, queue and save ran in this order
        InOrder order = inOrder(lock, limit, codes, emailQueue);
        order.verify(lock).acquire("ana@example.com");
        order.verify(limit).check(new CodeRequestContext("ana@example.com", SOURCE, NOW));
        order.verify(codes).invalidateLive("ana@example.com");
        order.verify(emailQueue).enqueue("{payload}", NOW);
        ArgumentCaptor<VerificationCode> code = ArgumentCaptor.forClass(VerificationCode.class);
        order.verify(codes).save(code.capture());
        // Then: the email payload carries the plain code
        verify(composer).toPayload(new CodeEmailPayload("ana@example.com", "042317"));
        // Then: the saved code is PENDING, holds only the hash and points to the queued outbox
        assertThat(code.getValue().getStatus()).isEqualTo(CodeStatus.PENDING);
        assertThat(code.getValue().getCodeHmac()).isEqualTo(hasher.hash("ana@example.com", "042317"));
        assertThat(code.getValue().getOutboxId()).isEqualTo(OUTBOX_ID);
    }

    /**
     * Proves a cooldown refusal tells the user exactly how long to wait and stores nothing, so
     * they know when to try again and no extra code is created.
     * <p>
     * Cases: cooldown violation with 42 s left → TooManyRequestsException saying "42 seconds";
     * no code saved. Only the cooldown may reveal the wait
     * (specs/001-request-code/spec.md FR-008; specs/001-request-code/contracts/auth-api.md 429).
     */
    @Test
    void cooldownViolationStatesTheWait() {
        // Given: the limit reports a cooldown violation with 42 seconds left
        when(limit.check(any())).thenReturn(Optional.of(new LimitViolation(ResendCooldownLimit.NAME, 42, true)));

        // When / Then: the request is refused with the wait in the message
        assertThatThrownBy(() -> service.request("ana@x.com", SOURCE))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("42 seconds");
        // Then: no code was saved
        verify(codes, never()).save(any());
    }

    /**
     * Proves every limit other than the cooldown is refused with a generic message and no wait,
     * so an attacker can't learn which limit they hit or when it resets.
     * <p>
     * Cases: GLOBAL_HOURLY violation → generic message, no Retry-After; no email queued
     * (specs/001-request-code/spec.md FR-020; specs/001-request-code/contracts/auth-api.md 429).
     */
    @Test
    void otherViolationsGetAGenericMessage() {
        // Given: a limit other than the cooldown is hit
        when(limit.check(any())).thenReturn(Optional.of(new LimitViolation("GLOBAL_HOURLY", 600, false)));

        // When / Then: the request is refused with the generic message and no wait time
        assertThatThrownBy(() -> service.request("ana@x.com", SOURCE))
                .isInstanceOfSatisfying(TooManyRequestsException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo(TooManyRequestsException.GENERIC_MESSAGE);
                    assertThat(exception.retryAfterSeconds()).isEmpty();
                });
        // Then: no email was queued
        verify(emailQueue, never()).enqueue(any(), any());
    }

    /**
     * Proves a request that can't get its email's lock in time is refused like a limit, with the
     * generic message, so a stuck request for one email turns its retries into quick 429s instead
     * of server errors, and the response reveals nothing about other requests.
     * <p>
     * Cases: the lock wait ends with lock_timeout → generic TooManyRequestsException, no
     * Retry-After; no limit checked, no code saved (specs/001-request-code/research.md R5;
     * specs/001-request-code/contracts/auth-api.md 429).
     */
    @Test
    void lockTimeoutIsRefusedWithTheGenericMessage() {
        // Given: another request for the email holds the lock past lock_timeout
        doThrow(new CannotAcquireLockException("canceling statement due to lock timeout"))
                .when(lock).acquire("ana@x.com");

        // When / Then: the request is refused with the generic message and no wait time
        assertThatThrownBy(() -> service.request("ana@x.com", SOURCE))
                .isInstanceOfSatisfying(TooManyRequestsException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo(TooManyRequestsException.GENERIC_MESSAGE);
                    assertThat(exception.retryAfterSeconds()).isEmpty();
                });
        // Then: nothing after the lock ran
        verify(limit, never()).check(any());
        verify(codes, never()).save(any());
    }
}
