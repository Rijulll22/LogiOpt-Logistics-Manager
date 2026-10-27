<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- Friendly error page. Messages come from WebSupport.error (errorMessage) or, for errors the
     container raises itself, from the status code. Exception details are never shown. --%>
<c:set var="status" value="${requestScope['jakarta.servlet.error.status_code']}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Error</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>Something went wrong</h1>
<c:choose>
    <c:when test="${not empty errorMessage}">
        <p><c:out value="${errorMessage}"/></p>
    </c:when>
    <c:when test="${status == 400}">
        <p>The request was not understood. Go back, check the form and try again.</p>
    </c:when>
    <c:when test="${status == 401}">
        <p>Please sign in to continue.</p>
    </c:when>
    <c:when test="${status == 403}">
        <p>This action is not allowed. Go back, reload the page and try again.</p>
    </c:when>
    <c:when test="${status == 404}">
        <p>The page you requested does not exist.</p>
    </c:when>
    <c:when test="${status == 405}">
        <p>This page cannot be used that way. Use the links and buttons on the LogiOpt pages.</p>
    </c:when>
    <c:when test="${status == 429}">
        <p>Too many attempts. Please wait a few minutes and try again.</p>
    </c:when>
    <c:when test="${status == 503}">
        <p>LogiOpt is temporarily unavailable. Please try again later.</p>
    </c:when>
    <c:otherwise>
        <p>An unexpected error occurred. Please try again later.</p>
    </c:otherwise>
</c:choose>
<p><a href="<c:url value='/dashboard'/>">Back to the dashboard</a></p>
</body>
</html>
