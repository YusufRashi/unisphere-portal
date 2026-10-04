package com.unisphere.common.web;

import jakarta.servlet.http.HttpServletRequest;

/** Request metadata recorded with sessions and audit entries. */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 255;

    public static ClientInfo from(HttpServletRequest request) {
        // With server.forward-headers-strategy=framework, getRemoteAddr() already reflects X-Forwarded-For
        // as set by our own reverse proxy.
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > MAX_USER_AGENT) {
            ua = ua.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), ua);
    }
}
