<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Tracking</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>Tracking of confirmed bookings</h1>
<c:if test="${not empty notice}"><p><strong><c:out value="${notice}"/></strong></p></c:if>
<p>Milestones follow one order: Booked, Picked up, In transit, At transfer (only for routes with more than one leg),
    Out for delivery, Delivered. Only the next milestone can be recorded; Delivered is final.
    <em>Advance (simulation)</em> records the next milestone with a standard description.</p>
<c:choose>
    <c:when test="${empty rows}"><p>No confirmed bookings yet.</p></c:when>
    <c:otherwise>
        <table border="1" cellpadding="4">
            <tr><th>Booking</th><th>Route</th><th>Milestones so far</th><th>Record next milestone</th><th>Simulation</th></tr>
            <c:forEach var="row" items="${rows}">
                <c:url var="viewUrl" value="/bookings/view"><c:param name="id" value="${row.booking.bookingId}"/></c:url>
                <tr>
                    <td><a href="<c:out value='${viewUrl}'/>"><c:out value="${row.booking.bookingId}"/></a>
                        (customer <c:out value="${row.booking.ownerUserId}"/>)</td>
                    <td><c:out value="${row.booking.snapshot.routeName}"/>, <c:out value="${row.booking.snapshot.legs.size()}"/> leg(s)</td>
                    <td><c:forEach var="e" items="${row.events}"><c:out value="${e.milestone.label}"/> (<c:out value="${e.source}"/>)<br></c:forEach></td>
                    <td>
                        <c:choose>
                            <c:when test="${empty row.next}">Delivered (final)</c:when>
                            <c:otherwise>
                                <form method="post" action="<c:url value='/admin/tracking'/>">
                                    <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                                    <input type="hidden" name="action" value="record">
                                    <input type="hidden" name="bookingId" value="<c:out value='${row.booking.bookingId}'/>">
                                    <input type="hidden" name="current" value="<c:out value='${row.currentKey}'/>">
                                    <input type="hidden" name="milestone" value="<c:out value='${row.next}'/>">
                                    <label>Next: <strong><c:out value="${row.next.label}"/></strong><br>
                                        Description <input type="text" name="description" maxlength="500" required></label>
                                    <button type="submit">Record <c:out value="${row.next.label}"/></button>
                                </form>
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <td>
                        <c:if test="${not empty row.next}">
                            <form method="post" action="<c:url value='/admin/tracking'/>">
                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                                <input type="hidden" name="action" value="advance">
                                <input type="hidden" name="bookingId" value="<c:out value='${row.booking.bookingId}'/>">
                                <input type="hidden" name="current" value="<c:out value='${row.currentKey}'/>">
                                <button type="submit">Advance (simulation)</button>
                            </form>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
        </table>
    </c:otherwise>
</c:choose>
</body>
</html>
