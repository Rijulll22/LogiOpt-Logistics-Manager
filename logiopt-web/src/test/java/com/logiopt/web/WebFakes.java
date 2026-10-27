package com.logiopt.web;

import com.logiopt.app.AppServices;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Servlet API fakes built with java.lang.reflect.Proxy (no extra libraries). Only the
 * methods LogiOpt calls are implemented; any other call fails the test with
 * UnsupportedOperationException, so unexpected API use is visible. JSPs are not rendered: a forward
 * is recorded as the target path.
 */
final class WebFakes {

    static final String CONTEXT_PATH = "/logiopt";
    private static final AtomicInteger SESSION_IDS = new AtomicInteger();

    private WebFakes() {
    }

    /** Server-side session. */
    static final class Session {
        String id = "S" + SESSION_IDS.incrementAndGet();
        final Map<String, Object> attributes = new HashMap<>();
        boolean invalidated;
        final HttpSession proxy;

        Session() {
            proxy = proxy(HttpSession.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getId":
                        return id;
                    case "getAttribute":
                        requireValid();
                        return attributes.get((String) a[0]);
                    case "setAttribute":
                        requireValid();
                        attributes.put((String) a[0], a[1]);
                        return null;
                    case "removeAttribute":
                        requireValid();
                        attributes.remove((String) a[0]);
                        return null;
                    case "invalidate":
                        requireValid();
                        invalidated = true;
                        attributes.clear();
                        return null;
                    case "isNew":
                        return false;
                    default:
                        return objectMethod(p, m.getName(), a);
                }
            });
        }

        private void requireValid() {
            if (invalidated) {
                throw new IllegalStateException("Session already invalidated");
            }
        }
    }

    /** Recorded response state. */
    static final class Response {
        int status = HttpServletResponse.SC_OK;
        String redirect;
        String forwardedTo;
        String contentType;
        long contentLength = -1;
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final HttpServletResponse proxy;

        Response() {
            proxy = proxy(HttpServletResponse.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "setStatus":
                        status = (Integer) a[0];
                        return null;
                    case "getStatus":
                        return status;
                    case "sendRedirect":
                        status = HttpServletResponse.SC_FOUND;
                        redirect = (String) a[0];
                        return null;
                    case "sendError":
                        status = (Integer) a[0];
                        return null;
                    case "isCommitted":
                        return redirect != null || body.size() > 0;
                    case "setContentType":
                        contentType = (String) a[0];
                        return null;
                    case "setHeader":
                    case "addHeader":
                        headers.put((String) a[0], (String) a[1]);
                        return null;
                    case "setDateHeader":
                        headers.put((String) a[0], String.valueOf(a[1]));
                        return null;
                    case "setContentLengthLong":
                        contentLength = (Long) a[0];
                        return null;
                    case "getOutputStream":
                        return new ServletOutputStream() {
                            @Override
                            public void write(int b) {
                                body.write(b);
                            }

                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setWriteListener(WriteListener listener) {
                                throw new UnsupportedOperationException("Fake does not implement async I/O");
                            }
                        };
                    case "setCharacterEncoding":
                        return null;
                    default:
                        return objectMethod(p, m.getName(), a);
                }
            });
        }
    }

    /** One request; {@link #session} is what the container would find for the request's cookie. */
    static final class Request {
        final String method;
        final String servletPath;
        final Map<String, String> params = new LinkedHashMap<>();
        final Map<String, Object> attributes = new HashMap<>();
        final Response response = new Response();
        Session session;
        String requestedSessionId;
        String remoteAddr = "192.0.2.10";
        final HttpServletRequest proxy;

        Request(String method, String servletPath) {
            this.method = method;
            this.servletPath = servletPath;
            proxy = proxy(HttpServletRequest.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getMethod":
                        return method;
                    case "getServletPath":
                        return servletPath;
                    case "getPathInfo":
                        return null;
                    case "getContextPath":
                        return CONTEXT_PATH;
                    case "getRequestURI":
                        return CONTEXT_PATH + servletPath;
                    case "getProtocol":
                        return "HTTP/1.1";
                    case "getDispatcherType":
                        return DispatcherType.REQUEST;
                    case "getRemoteAddr":
                        return remoteAddr;
                    case "getParameter":
                        return params.get((String) a[0]);
                    case "getAttribute":
                        return attributes.get((String) a[0]);
                    case "setAttribute":
                        attributes.put((String) a[0], a[1]);
                        return null;
                    case "removeAttribute":
                        attributes.remove((String) a[0]);
                        return null;
                    case "setCharacterEncoding":
                        return null;
                    case "getSession":
                        boolean create = a == null || a.length == 0 || (Boolean) a[0];
                        if (session != null && !session.invalidated) {
                            return session.proxy;
                        }
                        if (!create) {
                            return null;
                        }
                        session = new Session();
                        return session.proxy;
                    case "changeSessionId":
                        if (session == null || session.invalidated) {
                            throw new IllegalStateException("No session");
                        }
                        session.id = "S" + SESSION_IDS.incrementAndGet();
                        return session.id;
                    case "getRequestedSessionId":
                        return requestedSessionId;
                    case "isRequestedSessionIdValid":
                        return requestedSessionId != null && session != null && !session.invalidated
                                && requestedSessionId.equals(session.id);
                    case "getRequestDispatcher":
                        String path = (String) a[0];
                        return proxy(RequestDispatcher.class, (dp, dm, da) -> {
                            if ("forward".equals(dm.getName())) {
                                response.forwardedTo = path;
                                return null;
                            }
                            return objectMethod(dp, dm.getName(), da);
                        });
                    default:
                        return objectMethod(p, m.getName(), a);
                }
            });
        }

        Request param(String name, String value) {
            params.put(name, value);
            return this;
        }
    }

    /**
     * A client with a cookie jar: it sends its last session id, and the container finds the
     * session for that id unless it was invalidated (then the id is "requested but invalid").
     */
    static final class Browser {
        private final Map<String, Session> store;
        String cookie;

        Browser(Map<String, Session> store) {
            this.store = store;
        }

        Request request(String method, String path) {
            Request r = new Request(method, path);
            r.requestedSessionId = cookie;
            Session s = cookie == null ? null : store.get(cookie);
            r.session = s == null || s.invalidated ? null : s;
            return r;
        }

        /** Records the session the response left behind (new, rotated or invalidated). */
        void afterResponse(Request r) {
            if (r.session != null) {
                store.values().removeIf(s -> s == r.session);
                if (!r.session.invalidated) {
                    store.put(r.session.id, r.session);
                    cookie = r.session.id;
                }
            }
        }

        Session session() {
            Session s = cookie == null ? null : store.get(cookie);
            return s == null || s.invalidated ? null : s;
        }
    }

    /** @return a servlet context exposing the services */
    static ServletContext context(AppServices services) {
        return proxy(ServletContext.class, (p, m, a) -> {
            if ("getAttribute".equals(m.getName())) {
                return AppServices.ATTRIBUTE.equals(a[0]) ? services : null;
            }
            if ("log".equals(m.getName())) {
                return null;
            }
            return objectMethod(p, m.getName(), a);
        });
    }

    /** Initializes a filter with a fake FilterConfig. */
    static <F extends Filter> F init(F filter, ServletContext context) throws Exception {
        filter.init(proxy(FilterConfig.class, (p, m, a) -> {
            switch (m.getName()) {
                case "getServletContext":
                    return context;
                case "getFilterName":
                    return filter.getClass().getSimpleName();
                case "getInitParameter":
                    return null;
                case "getInitParameterNames":
                    return Collections.emptyEnumeration();
                default:
                    return objectMethod(p, m.getName(), a);
            }
        }));
        return filter;
    }

    /** Initializes a servlet with a fake ServletConfig. */
    static <S extends HttpServlet> S init(S servlet, ServletContext context) throws Exception {
        servlet.init(proxy(ServletConfig.class, (p, m, a) -> {
            switch (m.getName()) {
                case "getServletContext":
                    return context;
                case "getServletName":
                    return servlet.getClass().getSimpleName();
                case "getInitParameter":
                    return null;
                case "getInitParameterNames":
                    return Collections.emptyEnumeration();
                default:
                    return objectMethod(p, m.getName(), a);
            }
        }));
        return servlet;
    }

    /** A filter chain end that records whether it was reached. */
    static final class Terminal implements FilterChain {
        boolean reached;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse resp) {
            reached = true;
        }
    }

    /**
     * Runs a request through the given filters in order and then the servlet, like the container.
     *
     * @return true if the servlet was reached
     */
    static boolean run(Request r, List<Filter> filters, HttpServlet servlet) throws Exception {
        boolean[] reached = {false};
        FilterChain end = (req, resp) -> {
            reached[0] = true;
            if (servlet != null) {
                servlet.service(req, resp);
            }
        };
        FilterChain chain = end;
        for (int i = filters.size() - 1; i >= 0; i--) {
            Filter f = filters.get(i);
            FilterChain next = chain;
            chain = (req, resp) -> f.doFilter(req, resp, next);
        }
        chain.doFilter(r.proxy, r.response.proxy);
        return reached[0];
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(WebFakes.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object objectMethod(Object proxy, String name, Object[] args) {
        switch (name) {
            case "toString":
                return "fake@" + System.identityHashCode(proxy);
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                throw new UnsupportedOperationException("Fake does not implement " + name);
        }
    }
}
