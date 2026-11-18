package minhdoswe.tickgeek.common;

import java.util.Locale;

/** Emails are compared and stored trimmed and lowercase (FR-002). */
public final class EmailAddress {

    private EmailAddress() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
