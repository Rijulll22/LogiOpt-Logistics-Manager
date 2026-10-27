package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.app.SchemaGate;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SecurityHeadersFilter on its own and as the first filter of the web.xml chain: every response,
 * including a 503 from the schema gate and a sign-in redirect, carries the headers. No database.
 */
class SecurityHeadersFilterTest {

    private static final Map<String, String> EXPECTED = Map.of(
            "X-Frame-Options", "DENY",
            "X-Content-Type-Options", "nosniff",
            "Referrer-Policy", "same-origin",
            "Content-Security-Policy", SecurityHeadersFilter.CONTENT_SECURITY_POLICY,
            "Cache-Control", "no-store",
            "Pragma", "no-cache");

    private static void assertHeaders(WebFakes.Request r) {
        for (Map.Entry<String, String> e : EXPECTED.entrySet()) {
            assertEquals(e.getValue(), r.response.headers.get(e.getKey()), e.getKey());
        }
    }

    @Test
    void setsEveryHeaderAndContinuesTheChain() throws Exception {
        SecurityHeadersFilter filter = WebFakes.init(new SecurityHeadersFilter(), WebFakes.context(null));
        WebFakes.Request r = new WebFakes.Request("GET", "/login");
        assertTrue(WebFakes.run(r, List.of(filter), null));
        assertHeaders(r);
    }

    @Test
    void policyAllowsOnlyWhatThePagesUse() {
        String csp = SecurityHeadersFilter.CONTENT_SECURITY_POLICY;
        assertTrue(csp.startsWith("default-src 'none'"), "scripts, styles, frames, fonts and connections refused");
        assertTrue(csp.contains("form-action 'self'") && csp.contains("frame-ancestors 'none'")
                && csp.contains("base-uri 'none'"), csp);
        assertFalse(csp.contains("unsafe-inline") || csp.contains("unsafe-eval") || csp.contains("script-src"), csp);
    }

    @Test
    void headersAreOnA503AndOnASignInRedirectToo() throws Exception {
        SchemaGate behind = new SchemaGate(() -> 3, 7, "logiopt", Clock.systemUTC(), Duration.ofSeconds(10), () -> { });
        AppServices services = new AppServices(null, null, null, null, null, null, null, behind, null, null, null, null,
                null, null, null);
        List<Filter> chain = List.of(
                WebFakes.init(new SecurityHeadersFilter(), WebFakes.context(services)),
                WebFakes.init(new SchemaGateFilter(), WebFakes.context(services)));
        WebFakes.Request unavailable = new WebFakes.Request("GET", "/dashboard");
        assertFalse(WebFakes.run(unavailable, chain, null));
        assertEquals(503, unavailable.response.status);
        assertHeaders(unavailable);

        SchemaGate ready = new SchemaGate(() -> 7, 7, "logiopt", Clock.systemUTC(), Duration.ofSeconds(10), () -> { });
        AppServices up = new AppServices(null, null, null, null, null, null, null, ready, null, null, null, null,
                null, null, null);
        List<Filter> signIn = List.of(
                WebFakes.init(new SecurityHeadersFilter(), WebFakes.context(up)),
                WebFakes.init(new SchemaGateFilter(), WebFakes.context(up)),
                WebFakes.init(new AuthFilter(), WebFakes.context(up)));
        WebFakes.Request redirected = new WebFakes.Request("GET", "/dashboard");
        assertFalse(WebFakes.run(redirected, signIn, null));
        assertEquals("/logiopt/login", redirected.response.redirect);
        assertHeaders(redirected);
    }

    @Test
    void isTheFirstFilterInWebXml() throws Exception {
        assertEquals(List.of("com.logiopt.web.SecurityHeadersFilter", "com.logiopt.web.SchemaGateFilter",
                "com.logiopt.web.AuthFilter", "com.logiopt.web.CsrfFilter"), WebApp.filterClassesInWebXmlOrder());
    }
}
