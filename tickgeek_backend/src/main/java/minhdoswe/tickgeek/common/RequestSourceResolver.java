package minhdoswe.tickgeek.common;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import minhdoswe.tickgeek.config.FrontendProperties;

@Component
public class RequestSourceResolver {

    public static final String UNKNOWN = "unknown";
    public static final String CLIENT_IP_HEADER = "X-Tickgeek-Client-Ip";
    public static final String FRONTEND_SECRET_HEADER = "X-Tickgeek-Frontend-Secret";

    private final byte[] frontendSecret;

    public RequestSourceResolver(FrontendProperties frontend) {
        this.frontendSecret = frontend.secret().getBytes(StandardCharsets.UTF_8);
    }

    public String resolve(HttpServletRequest request) {
        if (fromFrontend(request)) {
            return ipLiteralOrUnknown(request.getHeader(CLIENT_IP_HEADER));
        }
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? UNKNOWN : address;
    }

    private boolean fromFrontend(HttpServletRequest request) {
        String secret = request.getHeader(FRONTEND_SECRET_HEADER);
        return secret != null && MessageDigest.isEqual(frontendSecret, secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String ipLiteralOrUnknown(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return InetAddress.ofLiteral(value.strip()).getHostAddress();
        } catch (IllegalArgumentException notAnIp) {
            return UNKNOWN;
        }
    }
}
