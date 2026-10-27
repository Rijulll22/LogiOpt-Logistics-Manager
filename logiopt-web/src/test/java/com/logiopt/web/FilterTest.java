package com.logiopt.web;

import com.logiopt.model.Role;
import com.logiopt.model.User;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AuthFilter and CsrfFilter in the web.xml order, with fake requests; no database.
 */
class FilterTest {

    private static final User CUSTOMER = new User(7, "c@example.test", "Customer", Role.CUSTOMER, true);
    private static final User ADMIN = new User(8, "admin@logiopt.local", "Administrator", Role.ADMIN, true);

    private List<Filter> filters;

    @BeforeEach
    void init() throws Exception {
        ServletContext context = WebFakes.context(null);
        filters = List.of(WebFakes.init(new AuthFilter(), context), WebFakes.init(new CsrfFilter(), context));
    }

    private static WebFakes.Request signedIn(String method, String path, User user) {
        WebFakes.Request r = new WebFakes.Request(method, path);
        r.session = new WebFakes.Session();
        r.session.attributes.put(WebSupport.SESSION_USER, user);
        r.session.attributes.put(CsrfFilter.SESSION_TOKEN, "token-1");
        r.requestedSessionId = r.session.id;
        return r;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/shipments", "/shipments/new", "/compare", "/reports", "/admin/users", "/index.jsp"})
    void anonymousRequestsToProtectedUrlsAreRedirectedToSignIn(String path) throws Exception {
        WebFakes.Request r = new WebFakes.Request("GET", path);
        assertFalse(WebFakes.run(r, filters, null));
        assertEquals("/logiopt/login", r.response.redirect);
    }

    @Test
    void anonymousPostIsRedirectedBeforeAnyHandlerRuns() throws Exception {
        WebFakes.Request r = new WebFakes.Request("POST", "/shipments/create").param("csrfToken", "x");
        assertFalse(WebFakes.run(r, filters, null));
        assertEquals("/logiopt/login", r.response.redirect);
    }

    @Test
    void expiredSessionIsRedirectedWithNotice() throws Exception {
        WebFakes.Request r = new WebFakes.Request("GET", "/shipments");
        r.requestedSessionId = "S-expired";
        assertFalse(WebFakes.run(r, filters, null));
        assertEquals("/logiopt/login?expired=1", r.response.redirect);
    }

    @Test
    void signInPageIsPublicAndGetsACsrfToken() throws Exception {
        WebFakes.Request r = new WebFakes.Request("GET", "/login");
        assertTrue(WebFakes.run(r, filters, null));
        String token = (String) r.attributes.get(CsrfFilter.PARAM);
        assertNotNull(token);
        assertTrue(token.length() >= 40, token);
        assertEquals(token, r.session.attributes.get(CsrfFilter.SESSION_TOKEN));
    }

    @Test
    void signedInUserReachesHandlerAndIsExposedFromTheSessionOnly() throws Exception {
        WebFakes.Request r = signedIn("GET", "/shipments", CUSTOMER).param("userId", "8");
        assertTrue(WebFakes.run(r, filters, null));
        assertSame(CUSTOMER, r.attributes.get(WebSupport.CURRENT_USER));
        assertEquals("token-1", r.attributes.get(CsrfFilter.PARAM));
    }

    @Test
    void customerGets404ForAdminUrlsAndAdminPasses() throws Exception {
        WebFakes.Request customer = signedIn("GET", "/admin/users", CUSTOMER);
        assertFalse(WebFakes.run(customer, filters, null));
        assertEquals(404, customer.response.status);
        assertEquals("/WEB-INF/jsp/error.jsp", customer.response.forwardedTo);
        WebFakes.Request admin = signedIn("GET", "/admin/users", ADMIN);
        assertTrue(WebFakes.run(admin, filters, null));
        WebFakes.Request lookalike = signedIn("GET", "/administrator", CUSTOMER);
        assertTrue(WebFakes.run(lookalike, filters, null), "only /admin and /admin/... are admin URLs");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "token-2", "token-1 "})
    void postWithMissingOrWrongTokenIsRejected(String token) throws Exception {
        WebFakes.Request r = signedIn("POST", "/logout", CUSTOMER);
        if (!token.isEmpty()) {
            r.param("csrfToken", token);
        }
        assertFalse(WebFakes.run(r, filters, null));
        assertEquals(403, r.response.status);
        assertEquals("/WEB-INF/jsp/error.jsp", r.response.forwardedTo);
    }

    @Test
    void postWithoutAnySessionIsRejectedEvenOnPublicPages() throws Exception {
        WebFakes.Request r = new WebFakes.Request("POST", "/login").param("csrfToken", "guess")
                .param("email", "c@example.test").param("password", "whatever1");
        assertFalse(WebFakes.run(r, filters, null));
        assertEquals(403, r.response.status);
    }

    @Test
    void postWithMatchingTokenPasses() throws Exception {
        WebFakes.Request r = signedIn("POST", "/logout", CUSTOMER).param("csrfToken", "token-1");
        assertTrue(WebFakes.run(r, filters, null));
    }

    @Test
    void rotateTokenIssuesANewRandomToken() {
        WebFakes.Session s = new WebFakes.Session();
        String first = CsrfFilter.rotateToken(s.proxy);
        String second = CsrfFilter.rotateToken(s.proxy);
        assertNotEquals(first, second);
        assertEquals(second, s.attributes.get(CsrfFilter.SESSION_TOKEN));
    }

    @Test
    void currentUserNeverComesFromRequestParameters() {
        WebFakes.Request r = new WebFakes.Request("GET", "/shipments").param("userId", "1")
                .param(WebSupport.SESSION_USER, "1");
        assertNull(WebSupport.currentUser(r.proxy));
    }
}
