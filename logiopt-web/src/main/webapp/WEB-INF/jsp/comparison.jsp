<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="shipment" value="${result.shipment}"/>
<c:set var="rec" value="${result.recommendation}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Route comparison</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>Route comparison for shipment <c:out value="${shipment.shipmentId}"/></h1>
<p><a href="<c:url value='/shipments'/>">All shipments</a> | <a href="<c:url value='/shipments/new'/>">New shipment</a></p>
<p>
    <c:out value="${shipment.source}"/> &rarr; <c:out value="${shipment.destination}"/>,
    <c:out value="${shipment.cargoType}"/>, <c:out value="${shipment.weightKg}"/> kg,
    dispatch <c:out value="${shipment.dispatchDate}"/>
</p>
<p><strong><c:out value="${estimateLabel}"/></strong></p>

<form method="get" action="<c:url value='/compare'/>">
    <input type="hidden" name="shipmentId" value="<c:out value='${shipment.shipmentId}'/>">
    <label for="strategy">Strategy</label>
    <select id="strategy" name="strategy">
        <c:forEach var="s" items="${strategies}">
            <option value="<c:out value='${s.key}'/>" <c:if test="${s.key == selectedStrategy}">selected</c:if>><c:out value="${s.displayName}"/></option>
        </c:forEach>
    </select>
    <button type="submit">Compare</button>
</form>
<form method="post" action="<c:url value='/reports'/>">
    <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
    <input type="hidden" name="shipmentId" value="<c:out value='${shipment.shipmentId}'/>">
    <input type="hidden" name="strategy" value="<c:out value='${selectedStrategy}'/>">
    <button type="submit">Generate TXT report (<c:out value="${rec.strategyName}"/>)</button>
</form>

<h2>Recommendation (<c:out value="${rec.strategyName}"/>)</h2>
<c:choose>
    <c:when test="${rec.recommended}">
        <p><mark><strong>Recommended: <c:out value="${rec.chosen.routeName}"/> (route <c:out value="${rec.chosen.routeId}"/>, <c:out value="${rec.chosen.mode.label}"/>)</strong></mark></p>
    </c:when>
    <c:otherwise>
        <p><strong>No route can be recommended.</strong></p>
    </c:otherwise>
</c:choose>
<p><c:out value="${rec.reason}"/></p>
<c:if test="${not empty rec.chosenScore}">
    <c:set var="cs" value="${rec.chosenScore}"/>
    <table border="1" cellpadding="4">
        <caption>Balanced score breakdown of the recommended route (lower is better; relative to the routes compared)</caption>
        <tr><th>Metric</th><th>Normalized</th><th>Weighted contribution</th></tr>
        <tr><td>Cost</td><td><fmt:formatNumber value="${cs.normalizedCost}" minFractionDigits="6" maxFractionDigits="6"/></td><td><fmt:formatNumber value="${cs.weightedCost}" minFractionDigits="6" maxFractionDigits="6"/></td></tr>
        <tr><td>Time</td><td><fmt:formatNumber value="${cs.normalizedTime}" minFractionDigits="6" maxFractionDigits="6"/></td><td><fmt:formatNumber value="${cs.weightedTime}" minFractionDigits="6" maxFractionDigits="6"/></td></tr>
        <tr><td>Emissions</td><td><fmt:formatNumber value="${cs.normalizedEmissions}" minFractionDigits="6" maxFractionDigits="6"/></td><td><fmt:formatNumber value="${cs.weightedEmissions}" minFractionDigits="6" maxFractionDigits="6"/></td></tr>
        <tr><td>Reliability</td><td><fmt:formatNumber value="${cs.normalizedReliability}" minFractionDigits="6" maxFractionDigits="6"/></td><td><fmt:formatNumber value="${cs.weightedReliability}" minFractionDigits="6" maxFractionDigits="6"/></td></tr>
        <tr><th colspan="2">Total score</th><th><fmt:formatNumber value="${cs.totalScore}" minFractionDigits="6" maxFractionDigits="6"/></th></tr>
    </table>
</c:if>

<h2>Book a route</h2>
<c:choose>
    <c:when test="${not empty activeBooking}">
        <c:url var="activeUrl" value="/bookings/view"><c:param name="id" value="${activeBooking.bookingId}"/></c:url>
        <p>This shipment already has an active booking: <a href="<c:out value='${activeUrl}'/>">booking <c:out value="${activeBooking.bookingId}"/></a> (<c:out value="${activeBooking.status}"/>).</p>
    </c:when>
    <c:when test="${not dateBookable}">
        <p>Booking needs a dispatch date of today or later. This shipment can still be compared and reported.</p>
    </c:when>
    <c:when test="${empty result.evaluations}">
        <p>No eligible route can be booked.</p>
    </c:when>
    <c:otherwise>
        <p>Booking saves a snapshot of the values computed on the server at that moment; a simulated payment then confirms it.</p>
        <c:forEach var="e" items="${result.evaluations}">
            <form method="post" action="<c:url value='/bookings'/>">
                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                <input type="hidden" name="shipmentId" value="<c:out value='${shipment.shipmentId}'/>">
                <input type="hidden" name="routeId" value="<c:out value='${e.routeId}'/>">
                <input type="hidden" name="strategy" value="<c:out value='${selectedStrategy}'/>">
                <button type="submit">Book <c:out value="${e.routeName}"/> (route <c:out value="${e.routeId}"/>, INR <c:out value="${e.totalCost}"/>)<c:if test="${rec.recommended and e.routeId == rec.chosen.routeId}"> - recommended</c:if></button>
            </form>
        </c:forEach>
    </c:otherwise>
</c:choose>

<h2>Eligible routes</h2>
<p><c:out value="${result.eligibility.message}"/></p>
<c:if test="${not empty result.evaluations}">
    <table border="1" cellpadding="4">
        <thead>
        <tr>
            <th>Route</th><th>Mode</th><th>Transport (INR)</th><th>Handling (INR)</th><th>Transfer (INR)</th>
            <th>Waiting (INR)</th><th>Total (INR)</th><th>Duration (h)</th><th>Estimated arrival</th>
            <th>Emissions (kg CO2e)</th><th>Reliability</th><th>Balanced score</th><th>Notes</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="e" items="${result.evaluations}">
            <c:set var="isChosen" value="${rec.recommended and e.routeId == rec.chosen.routeId}"/>
            <c:set var="score" value="${rec.scores[e.routeId]}"/>
            <c:set var="exclusion" value="${result.strategyExclusions[e.routeId]}"/>
            <tr>
                <td>
                    <c:if test="${isChosen}"><mark><strong>RECOMMENDED</strong></mark><br></c:if>
                    <c:out value="${e.routeName}"/> (route <c:out value="${e.routeId}"/>)
                </td>
                <td><c:out value="${e.mode.label}"/></td>
                <td><c:out value="${e.costBreakdown.transport}"/></td>
                <td><c:out value="${e.costBreakdown.handling}"/></td>
                <td><c:out value="${e.costBreakdown.transfer}"/></td>
                <td><c:out value="${e.costBreakdown.waiting}"/></td>
                <td><strong><c:out value="${e.totalCost}"/></strong></td>
                <td>
                    <c:choose>
                        <c:when test="${e.durationAvailable}"><fmt:formatNumber value="${e.durationHours}" minFractionDigits="2" maxFractionDigits="2"/></c:when>
                        <c:otherwise>not available</c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${not empty e.estimatedArrival}"><c:out value="${e.estimatedArrival}"/></c:when>
                        <c:otherwise>not available</c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${e.emissionsAvailable}"><fmt:formatNumber value="${e.emissionsKg}" minFractionDigits="2" maxFractionDigits="2"/></c:when>
                        <c:otherwise>not available</c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${e.reliabilityAvailable}"><c:out value="${e.reliability}"/></c:when>
                        <c:otherwise>not available</c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${not empty score}"><fmt:formatNumber value="${score.totalScore}" minFractionDigits="6" maxFractionDigits="6"/></c:when>
                        <c:otherwise>-</c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:forEach var="note" items="${e.notes}"><c:out value="${note}"/><br></c:forEach>
                    <c:if test="${not empty exclusion}">Not considered by <c:out value="${rec.strategyName}"/>: <c:out value="${exclusion}"/></c:if>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    <p><em>Estimated arrival assumes departure at 00:00 on the dispatch date. Balanced scores are relative to the routes currently compared.</em></p>
</c:if>

<h2>Routes not considered by the <c:out value="${rec.strategyName}"/> strategy</h2>
<c:choose>
    <c:when test="${empty rec.excluded}">
        <p>None: the strategy considered every eligible route.</p>
    </c:when>
    <c:otherwise>
        <ul>
            <c:forEach var="x" items="${rec.excluded}">
                <li><c:out value="${x.evaluation.routeName}"/> (route <c:out value="${x.evaluation.routeId}"/>): <c:out value="${x.reason}"/></li>
            </c:forEach>
        </ul>
    </c:otherwise>
</c:choose>

<h2>Routes rejected by eligibility</h2>
<c:choose>
    <c:when test="${empty result.rejections}">
        <p>None.</p>
    </c:when>
    <c:otherwise>
        <ul>
            <c:forEach var="r" items="${result.rejections}">
                <li><c:out value="${r.route.name}"/> (route <c:out value="${r.route.routeId}"/>): <c:out value="${r.reason}"/></li>
            </c:forEach>
        </ul>
    </c:otherwise>
</c:choose>

<p><strong><c:out value="${estimateLabel}"/></strong></p>
</body>
</html>
