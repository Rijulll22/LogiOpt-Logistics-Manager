package com.logiopt.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * First filter (declared in web.xml): adds browser security headers to every response before any
 * other filter or Servlet runs, so 503, redirect and error responses carry them too.
 * <ul>
 *   <li>X-Frame-Options DENY and CSP frame-ancestors 'none': no page can be framed.</li>
 *   <li>X-Content-Type-Options nosniff: browsers keep the declared content type.</li>
 *   <li>Referrer-Policy same-origin: URLs with ids are not sent to other sites.</li>
 *   <li>Content-Security-Policy: the pages are plain HTML with no script, no stylesheet, no inline
 *       style and no images except the favicon, so everything else is refused.</li>
 *   <li>Cache-Control no-store: every page is generated per user (and carries the session's CSRF
 *       token), so nothing is cached and the back button after sign-out shows no old page.</li>
 * </ul>
 * A Servlet may still replace a header for its own response (the report download does).
 */
public class SecurityHeadersFilter extends HttpFilter {

    private static final long serialVersionUID = 1L;

    /** Content-Security-Policy sent with every response. */
    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; img-src 'self'; form-action 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'";

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Referrer-Policy", "same-origin");
        resp.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        chain.doFilter(req, resp);
    }
}
