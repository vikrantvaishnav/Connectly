package com.connectly.security;

import jakarta.servlet.http.HttpServletRequest;

public final class WebUtil {
    private WebUtil() {}

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // first hop in the chain is the original client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null ? "unknown" : ua;
    }

    /** Short human-readable device label from the user agent. */
    public static String deviceLabel(HttpServletRequest request) {
        String ua = userAgent(request);
        String os;
        if (ua.contains("Android")) os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad")) os = "iOS";
        else if (ua.contains("Windows")) os = "Windows";
        else if (ua.contains("Mac OS")) os = "macOS";
        else if (ua.contains("Linux")) os = "Linux";
        else os = "Unknown OS";

        String browser;
        if (ua.contains("Edg/")) browser = "Edge";
        else if (ua.contains("Chrome/")) browser = "Chrome";
        else if (ua.contains("Firefox/")) browser = "Firefox";
        else if (ua.contains("Safari/")) browser = "Safari";
        else browser = "Browser";

        return browser + " · " + os;
    }
}
