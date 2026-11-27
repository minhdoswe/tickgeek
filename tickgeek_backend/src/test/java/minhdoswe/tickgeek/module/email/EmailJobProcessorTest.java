package minhdoswe.tickgeek.module.email;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import minhdoswe.tickgeek.module.email.emailsender.EmailSender;
import minhdoswe.tickgeek.support.MutableClock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailJobProcessorTest {

    private static final UUID ID = UUID.fromString("019a7a2c-0000-7000-8000-000000000009");
    private static final String PAYLOAD = "{payload}";
    private static final Instant START = MutableClock.START;

    private final OutboxJobSteps steps = mock(OutboxJobSteps.class);
    private final EmailSender sender = mock(EmailSender.class);
    private final CodeEmailComposer composer = mock(CodeEmailComposer.class);
    private final MutableClock clock = new MutableClock();
    private final EmailJobProcessor processor = new EmailJobProcessor(steps, sender, composer, clock);

    /**
     * Proves an attempt claims first, sends with no transaction around it, then records the
     * success at the time the provider accepted, so the code's 10 minutes start at acceptance.
     * <p>
     * Cases: claim at START → send to ana@x.com, taking 2 s → recordSuccess at START + 2 s
     * (specs/002-sending-code/research.md R2; spec.md FR-006).
     */
    @Test
    void claimsSendsThenRecordsTheSuccessAtAcceptance() {
        // Given: a claimable job for ana@x.com, and a send that takes 2 seconds
        when(steps.claim(ID, START)).thenReturn(Optional.of(PAYLOAD));
        when(composer.recipientOf(PAYLOAD)).thenReturn("ana@x.com");
        doAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(2));
            return null;
        }).when(sender).send(PAYLOAD, "ana@x.com");

        // When: the job is processed
        processor.process(ID);

        // Then: claim, send and record ran in order, the success at acceptance time
        InOrder order = inOrder(steps, sender);
        order.verify(steps).claim(ID, START);
        order.verify(sender).send(PAYLOAD, "ana@x.com");
        order.verify(steps).recordSuccess(ID, START.plusSeconds(2));
    }

    /**
     * Proves nothing is sent when the claim gives nothing, so finished, cancelled or not-yet-due
     * jobs never reach a provider.
     * <p>
     * Cases: empty claim → no send, nothing recorded (specs/002-sending-code/research.md R3).
     */
    @Test
    void sendsNothingWhenNothingIsClaimed() {
        // Given: nothing to claim
        when(steps.claim(ID, START)).thenReturn(Optional.empty());

        // When: the job is processed
        processor.process(ID);

        // Then: no send and no result
        verify(sender, never()).send(anyString(), anyString());
        verify(steps, never()).recordSuccess(any(), any());
    }

    /**
     * Proves a provider failure is recorded with its type and message but never the payload, so
     * the job retries and the stored error can't leak the code.
     * <p>
     * Cases: send throws IllegalStateException("provider unavailable") → recordFailure(id, START,
     * "IllegalStateException: provider unavailable"), no success
     * (specs/002-sending-code/spec.md FR-008; research.md R6).
     */
    @Test
    void aFailedSendIsRecordedWithoutThePayload() {
        // Given: a claimable job whose send fails
        when(steps.claim(ID, START)).thenReturn(Optional.of(PAYLOAD));
        when(composer.recipientOf(PAYLOAD)).thenReturn("ana@x.com");
        doThrow(new IllegalStateException("provider unavailable")).when(sender).send(PAYLOAD, "ana@x.com");

        // When: the job is processed
        processor.process(ID);

        // Then: the failure is recorded with type and message, and no success
        verify(steps).recordFailure(ID, START, "IllegalStateException: provider unavailable");
        verify(steps, never()).recordSuccess(any(), any());
    }
}
