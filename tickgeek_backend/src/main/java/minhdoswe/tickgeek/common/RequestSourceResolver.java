package minhdoswe.tickgeek.common;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The client IP used for per-source limits. Tomcat's RemoteIpValve has already replaced the
 * remote address with the forwarded one, but only for requests from trusted proxies (research R6).
 */
@Component
public class RequestSourceResolver {

    public static final String UNKNOWN = "unknown";

    public String resolve(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? UNKNOWN : address;
    }
}
