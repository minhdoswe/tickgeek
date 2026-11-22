package minhdoswe.tickgeek.module.auth.code;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import minhdoswe.tickgeek.config.AuthProperties;

@Component
public class CodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ":";

    private final SecretKeySpec key;

    public CodeHasher(AuthProperties properties) {
        this.key = new SecretKeySpec(properties.codeSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((email + SEPARATOR + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(ALGORITHM + " is not available", exception);
        }
    }
}
