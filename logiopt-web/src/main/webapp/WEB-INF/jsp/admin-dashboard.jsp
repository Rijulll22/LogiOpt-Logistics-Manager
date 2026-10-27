<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Admin dashboard</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>Admin dashboard</h1>
<c:choose>
    <c:when test="${not empty dashboardError}">
        <h2>Dashboard unavailable</h2>
        <p><c:out value="${dashboardError}"/></p>
        <p><a href="<c:url value='/admin/dashboard'/>">Try again</a></p>
    </c:when>
    <c:otherwise>
        <p>System-wide counts over all users, counted from the database just now.</p>

        <h2>Users (<c:out value="${dashboard.userTotal}"/>)</h2>
        <p>Accounts that can sign in; the disabled demo user is not counted.</p>
        <table border="1" cellpadding="4">
            <tr><th>Role</th><th>Count</th></tr>
            <c:forEach var="e" items="${dashboard.usersByRole}">
                <tr><td><c:out value="${e.key}"/></td><td><c:out value="${e.value}"/></td></tr>
            </c:forEach>
        </table>
        <p><a href="<c:url value='/admin/users'/>">All users</a></p>

        <h2>Bookings (<c:out value="${dashboard.bookingTotal}"/>)</h2>
        <c:choose>
            <c:when test="${dashboard.bookingTotal == 0}"><p>No bookings have been made yet.</p></c:when>
            <c:otherwise>
                <table border="1" cellpadding="4">
                    <tr><th>Status</th><th>Count</th></tr>
                    <c:forEach var="e" items="${dashboard.bookingsByStatus}">
                        <tr><td><c:out value="${e.key}"/></td><td><c:out value="${e.value}"/></td></tr>
                    </c:forEach>
                </table>
                <p><a href="<c:url value='/admin/bookings'/>">All bookings</a></p>
            </c:otherwise>
        </c:choose>

        <h2>Awaiting tracking</h2>
        <c:choose>
            <c:when test="${dashboard.confirmedAwaitingTracking == 0}">
                <p>No confirmed booking is waiting for tracking updates.</p>
            </c:when>
            <c:otherwise>
                <p><c:out value="${dashboard.confirmedAwaitingTracking}"/> confirmed booking(s) not yet delivered.
                    <a href="<c:url value='/admin/tracking'/>">Record tracking milestones</a></p>
            </c:otherwise>
        </c:choose>
    </c:otherwise>
</c:choose>
</body>
</html>
