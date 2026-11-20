package minhdoswe.tickgeek.module.auth.code;

import java.sql.SQLException;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Repository
public class CodeRequestLockRepository {

    static final int EMAIL_LOCKS = 0x74670002;
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private final JdbcTemplate jdbc;

    public CodeRequestLockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String email) {
        lock(EMAIL_LOCKS, email);
    }

    private void lock(int namespace, String value) {
        try {
            jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", resultSet -> null, namespace, value.hashCode());
        } catch (DataAccessException failure) {
            if (isLockTimeout(failure)) {
                throw new CannotAcquireLockException("Timed out waiting for the code request lock", failure);
            }
            throw failure;
        }
    }

    private static boolean isLockTimeout(DataAccessException failure) {
        return failure.getMostSpecificCause() instanceof SQLException sql
                && LOCK_NOT_AVAILABLE.equals(sql.getSQLState());
    }
}
