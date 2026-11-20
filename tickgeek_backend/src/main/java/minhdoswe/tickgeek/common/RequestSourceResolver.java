package minhdoswe.tickgeek.common;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RequestSourceResolver {

    public static final String UNKNOWN = "unknown";

    public String resolve(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? UNKNOWN : address;
    }
}
