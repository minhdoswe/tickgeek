package minhdoswe.tickgeek.module.auth.code;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import minhdoswe.tickgeek.module.auth.limit.CodeRequestContext;
import minhdoswe.tickgeek.support.TestProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeOutboxJobOwnerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final UUID OUTBOX_ID = UUID.fromString("019a7a2c-0000-7000-8000-000000000009");
    private static final long CODE_ID = 42L;

    private final VerificationCodeRepository codes = mock(VerificationCodeRepository.class);
    private final CodeOutboxJobOwner owner = new CodeOutboxJobOwner(codes, TestProperties.auth());

    /**
     * Proves only a pending code still wants its email, so a code that was replaced or used never
     * gets another send attempt.
     * <p>
     * Cases: PENDING → true; ACTIVE, USED, INVALIDATED → false
     * (specs/002-sending-code/research.md R3, R4).
     */
    @Test
    void onlyAPendingCodeStillWantsItsEmail() {
        for (CodeStatus status : CodeStatus.values()) {
            // Given: the outbox's code has this status
            when(codes.findByOutboxId(OUTBOX_ID)).thenReturn(Optional.of(code(status)));

            // When / Then: only PENDING still wants the email
            assertThat(owner.isStillWanted(OUTBOX_ID)).as(status.name()).isEqualTo(status == CodeStatus.PENDING);
        }
    }

    /**
     * Proves an outbox that belongs to no code is not blocked, so a future non-code email still
     * goes out.
     * <p>
     * Cases: no code for the outbox → wanted; delivered and abandoned change nothing
     * (specs/002-sending-code/research.md R4).
     */
    @Test
    void anOutboxWithoutACodeIsSentAndChangesNoCode() {
        // Given: no code points to the outbox
        when(codes.findByOutboxId(OUTBOX_ID)).thenReturn(Optional.empty());

        // When: it is checked, delivered and abandoned
        boolean wanted = owner.isStillWanted(OUTBOX_ID);
        owner.delivered(OUTBOX_ID, NOW);
        owner.abandoned(OUTBOX_ID);

        // Then: it is wanted and no code is touched
        assertThat(wanted).isTrue();
        verify(codes, never()).activatePending(anyLong(), any(), any());
        verify(codes, never()).invalidatePending(anyLong());
    }

    /**
     * Proves a delivered email makes exactly its own code live for the configured lifetime,
     * counted from when the provider accepted, so the user gets the full 10 minutes.
     * <p>
     * Cases: accepted at NOW → activatePending(42, NOW, NOW + 10 min), by the code's id
     * (specs/002-sending-code/spec.md FR-006; research.md R3).
     */
    @Test
    void deliveryActivatesThatCodeForTheConfiguredLifetime() {
        // Given: the outbox belongs to code 42
        when(codes.findByOutboxId(OUTBOX_ID)).thenReturn(Optional.of(code(CodeStatus.PENDING)));

        // When: the email was accepted at NOW
        owner.delivered(OUTBOX_ID, NOW);

        // Then: code 42 is activated until NOW + 10 minutes
        verify(codes).activatePending(CODE_ID, NOW, NOW.plusSeconds(600));
    }

    /**
     * Proves a job that gives up kills exactly its own code by id, so a newer code for the same
     * email stays usable.
     * <p>
     * Cases: abandoned → invalidatePending(42) (specs/002-sending-code/research.md R3, R4).
     */
    @Test
    void abandoningInvalidatesThatCodeById() {
        // Given: the outbox belongs to code 42
        when(codes.findByOutboxId(OUTBOX_ID)).thenReturn(Optional.of(code(CodeStatus.PENDING)));

        // When: the job gives up
        owner.abandoned(OUTBOX_ID);

        // Then: only code 42 is invalidated
        verify(codes).invalidatePending(CODE_ID);
    }

    private static VerificationCode code(CodeStatus status) {
        VerificationCode code = VerificationCode.pending(
                new CodeRequestContext("ana@example.com", "1.2.3.4", NOW), "hmac", OUTBOX_ID);
        ReflectionTestUtils.setField(code, "id", CODE_ID);
        ReflectionTestUtils.setField(code, "status", status);
        return code;
    }
}
