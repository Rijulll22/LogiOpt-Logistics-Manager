package com.logiopt.web;

import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebServlet;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the real web.xml and JSP sources: filter order, session and cookie settings, and a CSRF
 * field in every POST form. (Tomcat's own handling of these settings is checked separately; see README.)
 */
class WebXmlTest {

    private static final Path WEBAPP = Paths.get("src", "main", "webapp");

    private static Document webXml() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(WEBAPP.resolve("WEB-INF/web.xml").toFile());
    }

    private static List<String> texts(Element parent, String tag) {
        List<String> values = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagNameNS("*", tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent().trim());
        }
        return values;
    }

    @Test
    void filtersAreDeclaredInWebXmlInExplicitOrderAndNotByAnnotation() throws Exception {
        Element root = webXml().getDocumentElement();
        List<String> mappingOrder = new ArrayList<>();
        NodeList mappings = root.getElementsByTagNameNS("*", "filter-mapping");
        for (int i = 0; i < mappings.getLength(); i++) {
            Element m = (Element) mappings.item(i);
            mappingOrder.add(texts(m, "filter-name").get(0));
            assertEquals(List.of("/*"), texts(m, "url-pattern"));
        }
        assertEquals(List.of("SecurityHeadersFilter", "SchemaGateFilter", "AuthFilter", "CsrfFilter"), mappingOrder);
        assertEquals(List.of("com.logiopt.web.SecurityHeadersFilter", "com.logiopt.web.SchemaGateFilter",
                "com.logiopt.web.AuthFilter", "com.logiopt.web.CsrfFilter"), texts(root, "filter-class"));
        for (Class<?> c : List.of(SecurityHeadersFilter.class, SchemaGateFilter.class, AuthFilter.class,
                CsrfFilter.class)) {
            assertNull(c.getAnnotation(WebFilter.class), c.getName());
        }
    }

    @Test
    void friendlyErrorPagesAreDeclaredForEveryErrorStatus() throws Exception {
        Element root = webXml().getDocumentElement();
        assertEquals(List.of("400", "401", "403", "404", "405", "429", "500", "503"), texts(root, "error-code"));
        assertEquals(List.of("java.lang.Throwable"), texts(root, "exception-type"));
        assertTrue(texts(root, "location").stream().allMatch("/WEB-INF/jsp/error.jsp"::equals));
    }

    @Test
    void sessionCookieIsHttpOnlySameSiteLaxCookieOnlyWithTimeout() throws Exception {
        Element session = (Element) webXml().getDocumentElement().getElementsByTagNameNS("*", "session-config").item(0);
        assertEquals(List.of("30"), texts(session, "session-timeout"));
        assertEquals(List.of("true"), texts(session, "http-only"));
        assertEquals(List.of("SameSite"), texts(session, "attribute-name"));
        assertEquals(List.of("Lax"), texts(session, "attribute-value"));
        assertEquals(List.of("COOKIE"), texts(session, "tracking-mode"));
        assertEquals(List.of("UTF-8"), texts(webXml().getDocumentElement(), "request-character-encoding"));
    }

    @Test
    void authServletsAreMappedWhereTheFiltersExpectThem() {
        assertEquals("/login", LoginServlet.class.getAnnotation(WebServlet.class).value()[0]);
        assertEquals("/register", RegisterServlet.class.getAnnotation(WebServlet.class).value()[0]);
        assertEquals("/logout", LogoutServlet.class.getAnnotation(WebServlet.class).value()[0]);
        assertTrue(AdminUsersServlet.class.getAnnotation(WebServlet.class).value()[0].startsWith(AuthFilter.ADMIN_PREFIX + "/"));
        assertEquals(java.util.Set.of("/login", "/register"), AuthFilter.PUBLIC_PATHS);
    }

    @Test
    void everyPostFormCarriesTheCsrfTokenAndNoPageUsesScriptlets() throws Exception {
        Pattern form = Pattern.compile("(?is)<form\\b[^>]*method=\"post\"[^>]*>(.*?)</form>");
        int postForms = 0;
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().matches(".*\\.jspf?$"))::iterator) {
                String jsp = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = form.matcher(jsp);
                while (m.find()) {
                    postForms++;
                    assertTrue(m.group(1).contains("name=\"csrfToken\" value=\"<c:out value='${csrfToken}'/>\""),
                            file + " has a POST form without the CSRF field");
                }
                assertFalse(jsp.contains("<%=") || jsp.contains("<%!") || jsp.matches("(?s).*<%\\s.*"), file.toString());
                assertFalse(jsp.toLowerCase().contains("<script"), file.toString());
            }
        }
        assertEquals(12, postForms, "login, register, sign-out in the header, new or edited shipment, generate report, "
                + "book route, cancel booking, simulated payment, admin record milestone, admin advance, "
                + "mark all notifications read, mark one notification read");
    }
}
