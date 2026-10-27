<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - History</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>History</h1>
<p>Your own shipments, bookings and reports, newest first.</p>

<h2>Shipments</h2>
<p><em>Showing the latest <c:out value="${history.limit}"/> shipments at most.<c:if test="${history.moreShipments}"> Older shipments are not shown here; see <a href="<c:url value='/shipments'/>">My shipments</a>.</c:if></em></p>
<c:choose>
    <c:when test="${empty history.shipments}"><p>No shipments yet.</p></c:when>
    <c:otherwise>
        <table border="1" cellpadding="4">
            <tr><th>ID</th><th>Route</th><th>Cargo</th><th>Weight (kg)</th><th>Dispatch date</th><th>Status</th><th>Actions</th></tr>
            <c:forEach var="s" items="${history.shipments}">
                <c:url var="compareUrl" value="/compare">
                    <c:param name="shipmentId" value="${s.shipmentId}"/>
                    <c:param name="strategy" value="${defaultStrategy}"/>
                </c:url>
                <tr>
                    <td><c:out value="${s.shipmentId}"/></td>
                    <td><c:out value="${s.source}"/> &rarr; <c:out value="${s.destination}"/></td>
                    <td><c:out value="${s.cargoType}"/></td>
                    <td><c:out value="${s.weightKg}"/></td>
                    <td><c:out value="${s.dispatchDate}"/></td>
                    <td><c:out value="${s.status}"/></td>
                    <td><a href="<c:out value='${compareUrl}'/>">Compare</a><c:if test="${s.status == 'DRAFT'}"><c:url var="editUrl" value="/shipments/edit"><c:param name="shipmentId" value="${s.shipmentId}"/></c:url> | <a href="<c:out value='${editUrl}'/>">Edit draft</a></c:if></td>
                </tr>
            </c:forEach>
        </table>
    </c:otherwise>
</c:choose>

<h2>Bookings</h2>
<p><em>Showing the latest <c:out value="${history.limit}"/> bookings at most.<c:if test="${history.moreBookings}"> Older bookings are not shown here; see <a href="<c:url value='/bookings'/>">My bookings</a>.</c:if></em></p>
<c:choose>
    <c:when test="${empty history.bookings}"><p>No bookings yet.</p></c:when>
    <c:otherwise>
        <table border="1" cellpadding="4">
            <tr><th>Booking</th><th>Shipment</th><th>Route</th><th>Total (INR)</th><th>Status</th><th>Booked</th></tr>
            <c:forEach var="b" items="${history.bookings}">
                <c:url var="viewUrl" value="/bookings/view"><c:param name="id" value="${b.bookingId}"/></c:url>
                <tr>
                    <td><a href="<c:out value='${viewUrl}'/>"><c:out value="${b.bookingId}"/></a></td>
                    <td><c:out value="${b.shipmentId}"/>: <c:out value="${b.snapshot.source}"/> &rarr; <c:out value="${b.snapshot.destination}"/></td>
                    <td><c:out value="${b.snapshot.routeName}"/></td>
                    <td><c:out value="${b.snapshot.totalCost}"/></td>
                    <td><c:out value="${b.status}"/></td>
                    <td><c:out value="${b.bookedAt}"/></td>
                </tr>
            </c:forEach>
        </table>
    </c:otherwise>
</c:choose>

<h2>Reports</h2>
<p><em>Showing the latest <c:out value="${history.limit}"/> reports at most.<c:if test="${history.moreReports}"> Older reports are not shown.</c:if></em></p>
<c:choose>
    <c:when test="${empty history.reports}"><p>No reports yet. Generate one from a shipment's comparison page.</p></c:when>
    <c:otherwise>
        <table border="1" cellpadding="4">
            <tr><th>Requested</th><th>Shipment</th><th>Strategy</th><th>Status</th><th>Finished</th><th>Report</th></tr>
            <c:forEach var="r" items="${history.reports}">
                <c:url var="statusUrl" value="/reports"><c:param name="jobId" value="${r.jobId}"/></c:url>
                <tr>
                    <td><c:out value="${r.createdAt}"/></td>
                    <td><c:out value="${r.shipmentId}"/></td>
                    <td><c:out value="${r.strategyKey}"/></td>
                    <td><a href="<c:out value='${statusUrl}'/>"><c:out value="${r.status}"/></a></td>
                    <td><c:out value="${r.finishedAt}"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${r.done}"><c:url var="downloadUrl" value="/reports/download"><c:param name="jobId" value="${r.jobId}"/></c:url><a href="<c:out value='${downloadUrl}'/>">Download TXT</a></c:when>
                            <c:otherwise><c:out value="${empty r.errorMessage ? 'Not ready yet' : r.errorMessage}"/></c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
        </table>
    </c:otherwise>
</c:choose>
</body>
</html>
