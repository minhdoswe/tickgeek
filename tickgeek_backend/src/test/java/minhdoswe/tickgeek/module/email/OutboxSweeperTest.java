package minhdoswe.tickgeek.module.email;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxSweeperTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final UUID FIRST = UUID.fromString("019a7a2c-0000-7000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("019a7a2c-0000-7000-8000-000000000002");

    private final OutboxRepository outboxes = mock(OutboxRepository.class);
    private final EmailJobProcessor processor = mock(EmailJobProcessor.class);
    private final OutboxSweeper sweeper = new OutboxSweeper(outboxes, processor, Clock.fixed(NOW, ZoneOffset.UTC));

    /**
     * Proves every due job found by a sweep is run, so a job whose event was lost is still sent.
     * <p>
     * Cases: two jobs due at NOW → both processed (specs/002-sending-code/research.md R1).
     */
    @Test
    void runsEveryDueJob() {
        // Given: two jobs due now
        when(outboxes.findDueIds(NOW, OutboxSweeper.BATCH_SIZE)).thenReturn(List.of(FIRST, SECOND));

        // When: a sweep runs
        sweeper.sweep();

        // Then: both are processed
        verify(processor).process(FIRST);
        verify(processor).process(SECOND);
    }

    /**
     * Proves one job failing does not stop the rest of the sweep.
     * <p>
     * Cases: first job throws → second still processed (specs/002-sending-code/research.md R1).
     */
    @Test
    void oneFailingJobDoesNotStopTheRest() {
        // Given: two due jobs, and the first one throws
        when(outboxes.findDueIds(NOW, OutboxSweeper.BATCH_SIZE)).thenReturn(List.of(FIRST, SECOND));
        doThrow(new IllegalStateException("database down")).when(processor).process(FIRST);

        // When: a sweep runs
        sweeper.sweep();

        // Then: the second job is still processed
        verify(processor).process(SECOND);
    }
}
