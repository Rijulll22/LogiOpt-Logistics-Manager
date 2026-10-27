<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - All bookings</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>All bookings (read-only)</h1>
<c:choose>
    <c:when test="${empty bookings}"><p>No bookings yet.</p></c:when>
    <c:otherwise>
        <table border="1" cellpadding="4">
            <tr><th>Booking</th><th>Customer (user id)</th><th>Shipment</th><th>Route</th><th>Total (INR)</th><th>Status</th><th>Booked</th></tr>
            <c:forEach var="b" items="${bookings}">
                <c:url var="viewUrl" value="/bookings/view"><c:param name="id" value="${b.bookingId}"/></c:url>
                <tr>
                    <td><a href="<c:out value='${viewUrl}'/>"><c:out value="${b.bookingId}"/></a></td>
                    <td><c:out value="${b.ownerUserId}"/></td>
                    <td><c:out value="${b.shipmentId}"/>: <c:out value="${b.snapshot.source}"/> &rarr; <c:out value="${b.snapshot.destination}"/></td>
                    <td><c:out value="${b.snapshot.routeName}"/> (route <c:out value="${b.snapshot.routeId}"/>)</td>
                    <td><c:out value="${b.snapshot.totalCost}"/></td>
                    <td><c:out value="${b.status}"/></td>
                    <td><c:out value="${b.bookedAt}"/></td>
                </tr>
            </c:forEach>
        </table>
    </c:otherwise>
</c:choose>
</body>
</html>
