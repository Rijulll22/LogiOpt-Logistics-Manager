package com.logiopt.web;

import com.logiopt.app.SchemaGate;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * First filter (declared in web.xml): answers every request with 503 and the migration instructions
 * while the database schema is behind the version this build needs.
 */
public class SchemaGateFilter extends HttpFilter {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        SchemaGate gate = WebSupport.services(getServletContext()).getSchemaGate();
        if (gate.isReady()) {
            chain.doFilter(req, resp);
            return;
        }
        WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, gate.getMessage());
    }
}
