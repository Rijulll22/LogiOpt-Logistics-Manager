<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Dashboard</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>Dashboard</h1>
<c:choose>
    <c:when test="${not empty dashboardError}">
        <h2>Dashboard unavailable</h2>
        <p><c:out value="${dashboardError}"/></p>
        <p><a href="<c:url value='/dashboard'/>">Try again</a></p>
    </c:when>
    <c:otherwise>
        <p>Your own shipments, bookings and notifications, counted from the database just now.</p>

        <h2>Shipments (<c:out value="${dashboard.shipmentTotal}"/>)</h2>
        <c:choose>
            <c:when test="${dashboard.shipmentTotal == 0}">
                <p>You have no shipments yet. <a href="<c:url value='/shipments/new'/>">Create your first shipment</a>.</p>
            </c:when>
            <c:otherwise>
                <table border="1" cellpadding="4">
                    <tr><th>Status</th><th>Count</th></tr>
                    <c:forEach var="e" items="${dashboard.shipmentsByStatus}">
                        <tr><td><c:out value="${e.key}"/></td><td><c:out value="${e.value}"/></td></tr>
                    </c:forEach>
                </table>
            </c:otherwise>
        </c:choose>

        <h2>Bookings (<c:out value="${dashboard.bookingTotal}"/>)</h2>
        <c:choose>
            <c:when test="${dashboard.bookingTotal == 0}">
                <p>You have no bookings yet. Book a route from a shipment's comparison page.</p>
            </c:when>
            <c:otherwise>
                <table border="1" cellpadding="4">
                    <tr><th>Status</th><th>Count</th></tr>
                    <c:forEach var="e" items="${dashboard.bookingsByStatus}">
                        <tr><td><c:out value="${e.key}"/></td><td><c:out value="${e.value}"/></td></tr>
                    </c:forEach>
                </table>
            </c:otherwise>
        </c:choose>

        <h2>Notifications</h2>
        <p><a href="<c:url value='/notifications'/>"><c:out value="${dashboard.unreadNotifications}"/> unread</a></p>

        <h2>Tracking of confirmed bookings</h2>
        <c:choose>
            <c:when test="${empty dashboard.tracking}">
                <p>No confirmed bookings yet. A booking is confirmed by a successful (simulated) payment.</p>
            </c:when>
            <c:otherwise>
                <table border="1" cellpadding="4">
                    <tr><th>Booking</th><th>Shipment</th><th>Route</th><th>Latest milestone</th><th>Recorded</th></tr>
                    <c:forEach var="t" items="${dashboard.tracking}">
                        <c:url var="viewUrl" value="/bookings/view"><c:param name="id" value="${t.bookingId}"/></c:url>
                        <tr>
                            <td><a href="<c:out value='${viewUrl}'/>"><c:out value="${t.bookingId}"/></a></td>
                            <td><c:out value="${t.shipmentId}"/>: <c:out value="${t.source}"/> &rarr; <c:out value="${t.destination}"/></td>
                            <td><c:out value="${t.routeName}"/></td>
                            <td><c:out value="${empty t.latest ? 'No milestone yet' : t.latest.label}"/></td>
                            <td><c:out value="${t.latestAt}"/></td>
                        </tr>
                    </c:forEach>
                </table>
                <c:if test="${dashboard.trackingTruncated}">
                    <p><em>Showing the latest <c:out value="${dashboard.trackingLimit}"/> confirmed bookings; see
                        <a href="<c:url value='/bookings'/>">My bookings</a> for all.</em></p>
                </c:if>
            </c:otherwise>
        </c:choose>
        <p><a href="<c:url value='/history'/>">History of shipments, bookings and reports</a></p>
    </c:otherwise>
</c:choose>
</body>
</html>
