<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="s" value="${booking.snapshot}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Booking</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>Booking <c:out value="${booking.bookingId}"/></h1>
<c:if test="${not empty notice}"><p><strong><c:out value="${notice}"/></strong></p></c:if>
<c:if test="${readOnlyAdmin}"><p><em>Administrator view: read-only. Only the customer can pay or cancel.</em></p></c:if>
<p>Status: <strong><c:out value="${booking.status}"/></strong>
    | booked <c:out value="${booking.bookedAt}"/>
    <c:if test="${not empty booking.confirmedAt}">| confirmed <c:out value="${booking.confirmedAt}"/></c:if>
    <c:if test="${not empty booking.cancelledAt}">| cancelled <c:out value="${booking.cancelledAt}"/></c:if>
</p>

<c:if test="${canAct}">
    <c:url var="payUrl" value="/payments"><c:param name="bookingId" value="${booking.bookingId}"/></c:url>
    <p><a href="<c:out value='${payUrl}'/>">Pay INR <c:out value="${s.totalCost}"/> (simulation)</a></p>
    <form method="post" action="<c:url value='/bookings/cancel'/>">
        <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
        <input type="hidden" name="id" value="<c:out value='${booking.bookingId}'/>">
        <button type="submit">Cancel this unpaid booking</button>
    </form>
</c:if>

<h2>Tracking</h2>
<c:choose>
    <c:when test="${empty timeline}">
        <p>No tracking yet. Tracking starts when the booking is paid (confirmed).</p>
    </c:when>
    <c:otherwise>
        <table border="1" cellpadding="4">
            <tr><th>When</th><th>Milestone</th><th>Description</th><th>Source</th></tr>
            <c:forEach var="e" items="${timeline}">
                <tr>
                    <td><c:out value="${e.eventTime}"/></td>
                    <td><c:out value="${e.milestone.label}"/></td>
                    <td><c:out value="${e.description}"/></td>
                    <td><c:out value="${e.source}"/></td>
                </tr>
            </c:forEach>
        </table>
        <c:choose>
            <c:when test="${not empty nextMilestone}"><p>Next milestone: <c:out value="${nextMilestone.label}"/></p></c:when>
            <c:otherwise><p>Delivered. Tracking is complete.</p></c:otherwise>
        </c:choose>
    </c:otherwise>
</c:choose>

<h2>Snapshot taken at booking time</h2>
<p><em>These values were computed on the server when the booking was made. Later route changes do not alter them.</em></p>
<p>Shipment <c:out value="${booking.shipmentId}"/>: <c:out value="${s.source}"/> &rarr; <c:out value="${s.destination}"/>,
    <c:out value="${s.cargoType}"/>, <fmt:formatNumber value="${s.weightKg}" minFractionDigits="3" maxFractionDigits="3" groupingUsed="false"/> kg,
    dispatch <c:out value="${s.dispatchDate}"/></p>
<p>Route: <strong><c:out value="${s.routeName}"/></strong> (route <c:out value="${s.routeId}"/>, <c:out value="${s.routeMode.label}"/>),
    strategy <c:out value="${s.strategyName}"/> (<c:out value="${s.strategyKey}"/>)</p>
<p>Reason: <c:out value="${s.reason}"/></p>
<table border="1" cellpadding="4">
    <tr><th>Transport (INR)</th><th>Handling (INR)</th><th>Transfer (INR)</th><th>Waiting (INR)</th><th>Total (INR)</th>
        <th>Duration (h)</th><th>Estimated arrival</th><th>Emissions (kg CO2e)</th><th>Reliability</th><th>Balanced score</th></tr>
    <tr>
        <td><c:out value="${s.cost.transport}"/></td>
        <td><c:out value="${s.cost.handling}"/></td>
        <td><c:out value="${s.cost.transfer}"/></td>
        <td><c:out value="${s.cost.waiting}"/></td>
        <td><strong><c:out value="${s.totalCost}"/></strong></td>
        <td><c:choose><c:when test="${not empty s.durationHours}"><fmt:formatNumber value="${s.durationHours}" minFractionDigits="2" maxFractionDigits="2"/></c:when><c:otherwise>not available</c:otherwise></c:choose></td>
        <td><c:choose><c:when test="${not empty s.estimatedArrival}"><c:out value="${s.estimatedArrival}"/></c:when><c:otherwise>not available</c:otherwise></c:choose></td>
        <td><c:choose><c:when test="${not empty s.emissionsKg}"><fmt:formatNumber value="${s.emissionsKg}" minFractionDigits="2" maxFractionDigits="2"/></c:when><c:otherwise>not available</c:otherwise></c:choose></td>
        <td><c:choose><c:when test="${not empty s.reliability}"><fmt:formatNumber value="${s.reliability}" minFractionDigits="3" maxFractionDigits="3"/></c:when><c:otherwise>not available</c:otherwise></c:choose></td>
        <td><c:choose><c:when test="${not empty s.balancedScore}"><fmt:formatNumber value="${s.balancedScore}" minFractionDigits="6" maxFractionDigits="6"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
    </tr>
</table>
<c:if test="${not empty s.notes}"><p>Notes: <c:out value="${s.notes}"/></p></c:if>
<h3>Legs</h3>
<table border="1" cellpadding="4">
    <tr><th>#</th><th>Mode</th><th>Distance (km)</th><th>Rate (INR per tonne-km)</th><th>Speed (km/h)</th><th>Buffer (h)</th><th>Emission factor (kg CO2e per tonne-km)</th></tr>
    <c:forEach var="leg" items="${s.legs}" varStatus="st">
        <tr>
            <td><c:out value="${st.count}"/></td>
            <td><c:out value="${leg.mode.label}"/></td>
            <td><c:out value="${leg.distanceKm}"/></td>
            <td><c:out value="${leg.ratePerTonneKm}"/></td>
            <td><c:choose><c:when test="${not empty leg.speedKmh}"><c:out value="${leg.speedKmh}"/></c:when><c:otherwise>not available</c:otherwise></c:choose></td>
            <td><c:out value="${leg.bufferHours}"/></td>
            <td><c:choose><c:when test="${not empty leg.emissionFactorKgPerTonneKm}"><c:out value="${leg.emissionFactorKgPerTonneKm}"/></c:when><c:otherwise>not available</c:otherwise></c:choose></td>
        </tr>
    </c:forEach>
</table>

<h2>Payment attempts (simulated)</h2>
<c:choose>
    <c:when test="${empty payments}"><p>No payment attempts yet.</p></c:when>
    <c:otherwise>
        <table border="1" cellpadding="4">
            <tr><th>Attempt</th><th>Method</th><th>Amount (INR)</th><th>Status</th><th>Started</th><th>Paid</th><th>Failed</th></tr>
            <c:forEach var="p" items="${payments}">
                <tr>
                    <td><c:out value="${p.attemptNo}"/></td>
                    <td><c:out value="${p.method.label}"/></td>
                    <td><c:out value="${p.amount}"/></td>
                    <td><c:out value="${p.status}"/></td>
                    <td><c:out value="${p.createdAt}"/></td>
                    <td><c:out value="${p.paidAt}"/></td>
                    <td><c:out value="${p.failedAt}"/></td>
                </tr>
            </c:forEach>
        </table>
    </c:otherwise>
</c:choose>
<p><strong>Estimates based on configured sample rates and factors; not live carrier data.</strong></p>
</body>
</html>
