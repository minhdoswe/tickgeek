package minhdoswe.tickgeek.support;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfig.class)
public abstract class AbstractIntegrationTest {

    public static final String CODE_SECRET = "test-code-secret-at-least-32-bytes-long";
    public static final String FRONTEND_SECRET = "test-frontend-secret-at-least-32-bytes";
    protected static final String DEFAULT_SOURCE = "203.0.113.7";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.CONTAINER;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected RecordingEmailSender emailSender;

    @Autowired
    protected ControllableTaskScheduler scheduler;

    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("tickgeek.auth.code-secret", () -> CODE_SECRET);
        registry.add("tickgeek.frontend.secret", () -> FRONTEND_SECRET);
        registry.add("tickgeek.email.brevo.api-key", () -> "test-brevo-api-key");
        registry.add("tickgeek.email.brevo.sender-email", () -> "no-reply@tickgeek.test");
    }

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE outboxes, verification_codes RESTART IDENTITY CASCADE");
        clock.reset();
        emailSender.reset();
        scheduler.clear();
    }

    protected ResultActions postJson(String path, String json) throws Exception {
        return postJson(path, json, DEFAULT_SOURCE);
    }

    protected ResultActions postJson(String path, String json, String source) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .with(request -> {
                    request.setRemoteAddr(source);
                    return request;
                }));
    }

    protected ResultActions requestCode(String email) throws Exception {
        return postJson("/api/auth/code-requests", "{\"email\":\"%s\"}".formatted(email));
    }

    protected static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    protected int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
