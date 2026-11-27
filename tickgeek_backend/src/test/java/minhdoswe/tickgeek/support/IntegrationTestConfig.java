package minhdoswe.tickgeek.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public ControllableTaskScheduler controllableTaskScheduler(MutableClock clock) {
        return new ControllableTaskScheduler(clock);
    }

    /** Primary so it replaces the Brevo sender, which also exists outside the local profile. */
    @Bean
    @Primary
    public RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }
}
