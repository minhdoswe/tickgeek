package minhdoswe.tickgeek.module.auth;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
public class DebugCodeController {

    private final JdbcTemplate jdbc;

    public DebugCodeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/codes")
    public List<String> codes(@RequestParam String email) {
        return jdbc.queryForList(
                "SELECT code_hmac FROM verification_codes WHERE email = '" + email + "'", String.class);
    }
}
