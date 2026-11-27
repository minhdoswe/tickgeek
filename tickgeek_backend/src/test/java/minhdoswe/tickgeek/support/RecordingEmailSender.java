package minhdoswe.tickgeek.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import minhdoswe.tickgeek.module.email.emailsender.EmailSender;

/** An email sender for tests: records every call and fails as many times as it is told to. */
public class RecordingEmailSender implements EmailSender {

    public static final String FAILURE_MESSAGE = "provider unavailable";
    private static final int ALWAYS = Integer.MAX_VALUE;

    private final List<SentEmail> calls = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();

    public record SentEmail(String payload, String to) {
    }

    @Override
    public void send(String payload, String to) {
        calls.add(new SentEmail(payload, to));
        if (failuresLeft.getAndUpdate(left -> left == ALWAYS || left == 0 ? left : left - 1) > 0) {
            throw new IllegalStateException(FAILURE_MESSAGE);
        }
    }

    public void failNext(int times) {
        failuresLeft.set(times);
    }

    public void alwaysFail() {
        failuresLeft.set(ALWAYS);
    }

    public List<SentEmail> calls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
        failuresLeft.set(0);
    }
}
