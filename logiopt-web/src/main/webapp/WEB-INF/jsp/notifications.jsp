<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Notifications</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>Notifications</h1>
<c:choose>
    <c:when test="${empty notifications}"><p>No notifications.</p></c:when>
    <c:otherwise>
        <form method="post" action="<c:url value='/notifications'/>">
            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
            <input type="hidden" name="action" value="readAll">
            <button type="submit">Mark all as read</button>
        </form>
        <table border="1" cellpadding="4">
            <tr><th>When</th><th>Message</th><th>Booking</th><th>Status</th></tr>
            <c:forEach var="n" items="${notifications}">
                <c:url var="bookingUrl" value="/bookings/view"><c:param name="id" value="${n.bookingId}"/></c:url>
                <tr>
                    <td><c:out value="${n.createdAt}"/></td>
                    <td><c:choose><c:when test="${n.read}"><c:out value="${n.message}"/></c:when><c:otherwise><strong><c:out value="${n.message}"/></strong></c:otherwise></c:choose></td>
                    <td><a href="<c:out value='${bookingUrl}'/>"><c:out value="${n.bookingId}"/></a></td>
                    <td>
                        <c:choose>
                            <c:when test="${n.read}">read</c:when>
                            <c:otherwise>
                                <form method="post" action="<c:url value='/notifications'/>">
                                    <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                                    <input type="hidden" name="action" value="read">
                                    <input type="hidden" name="id" value="<c:out value='${n.notificationId}'/>">
                                    <button type="submit">Mark as read</button>
                                </form>
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
        </table>
    </c:otherwise>
</c:choose>
</body>
</html>
