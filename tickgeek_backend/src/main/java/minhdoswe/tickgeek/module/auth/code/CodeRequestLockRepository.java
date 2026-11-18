package minhdoswe.tickgeek.module.auth.code;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Repository
public class CodeRequestLockRepository {

    static final int EMAIL_LOCKS = 0x74670002;

    private final JdbcTemplate jdbc;

    public CodeRequestLockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String email) {
        lock(EMAIL_LOCKS, email);
    }

    private void lock(int namespace, String value) {
        jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", resultSet -> null, namespace, value.hashCode());
    }
}
