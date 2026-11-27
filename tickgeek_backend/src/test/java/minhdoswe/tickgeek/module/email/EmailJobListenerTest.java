package minhdoswe.tickgeek.module.email;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import minhdoswe.tickgeek.support.MutableClock;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailJobListenerTest {

    private static final UUID ID = UUID.fromString("019a7a2c-0000-7000-8000-000000000009");

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final EmailJobProcessor processor = mock(EmailJobProcessor.class);
    private final EmailJobListener listener = new EmailJobListener(scheduler, processor, new MutableClock());

    /**
     * Proves an announced job runs after its delay, measured on the app clock, so retries wait 5,
     * 10, 20 and 40 seconds.
     * <p>
     * Cases: job announced with delay 5 s → scheduled at START + 5 s, and the task processes that
     * job (specs/002-sending-code/research.md R1).
     */
    @Test
    void schedulesTheJobAfterItsDelay() {
        // When: a job is announced with a 5-second delay
        listener.dispatch(new OutboxJobScheduled(ID, Duration.ofSeconds(5)));

        // Then: it is scheduled at START + 5 s
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), eq(MutableClock.START.plusSeconds(5)));

        // Then: running the task processes that job
        task.getValue().run();
        verify(processor).process(ID);
    }
}
