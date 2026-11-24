package minhdoswe.tickgeek.module.email;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeEmailQueueTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final UUID SAVED_ID = UUID.fromString("019a7a2c-0000-7000-8000-000000000009");

    private final OutboxRepository outboxes = mock(OutboxRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CodeEmailQueue queue = new CodeEmailQueue(outboxes, events);

    /**
     * Proves queuing an email first saves a PENDING outbox row, so a code email isn't lost when
     * sending fails: the row records what still has to go out.
     * <p>
     * Cases: enqueue a payload → a PENDING row with the payload is saved, its id is returned so
     * the code can point to it, and an event to send that row now is published with the id it
     * got on save. The event only starts the send once the request commits
     * (specs/001-request-code/research.md R9).
     */
    @Test
    void savesAPendingJobAndAnnouncesItForNow() {
        // Given: saving sets the id, as Hibernate's UUIDv7 generator does on persist
        when(outboxes.save(any())).thenAnswer(invocation -> {
            Outbox job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", SAVED_ID);
            return job;
        });

        // When: an email is queued
        UUID returned = queue.enqueue("{payload}", NOW);

        // Then: a PENDING row with the payload was saved, and its id was returned
        ArgumentCaptor<Outbox> saved = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxes).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getValue().getPayload()).isEqualTo("{payload}");
        assertThat(returned).isEqualTo(SAVED_ID);
        // Then: an event to send that row now was published with the id set on save
        verify(events).publishEvent(OutboxJobScheduled.now(SAVED_ID));
    }
}
