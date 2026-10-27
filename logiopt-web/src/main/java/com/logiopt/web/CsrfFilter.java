package com.logiopt.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Third filter (declared in web.xml): keeps one random CSRF token per session, exposes it to JSPs
 * as the request attribute "csrfToken", and rejects every state-changing request (anything but GET,
 * HEAD, OPTIONS, TRACE) whose hidden form field "csrfToken" does not match it, with 403.
 */
public class CsrfFilter extends HttpFilter {

    private static final long serialVersionUID = 1L;

    /** Session attribute holding the token. */
    public static final String SESSION_TOKEN = "logiopt.csrf";
    /** Form field and request attribute name. */
    public static final String PARAM = "csrfToken";

    static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Replaces the session's token, for example right after sign-in.
     *
     * @param session session
     * @return the new token
     */
    public static String rotateToken(HttpSession session) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_TOKEN, token);
        return token;
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpSession existing = req.getSession(false);
        Object stored = existing == null ? null : existing.getAttribute(SESSION_TOKEN);
        if (!SAFE_METHODS.contains(req.getMethod())) {
            String sent = req.getParameter(PARAM);
            if (!(stored instanceof String) || sent == null || !MessageDigest.isEqual(
                    ((String) stored).getBytes(StandardCharsets.UTF_8), sent.getBytes(StandardCharsets.UTF_8))) {
                WebSupport.error(req, resp, HttpServletResponse.SC_FORBIDDEN,
                        "This form has expired or was not sent from a LogiOpt page. Go back, reload the page and try again.");
                return;
            }
        }
        String token = stored instanceof String ? (String) stored : rotateToken(req.getSession(true));
        req.setAttribute(PARAM, token);
        chain.doFilter(req, resp);
    }
}
