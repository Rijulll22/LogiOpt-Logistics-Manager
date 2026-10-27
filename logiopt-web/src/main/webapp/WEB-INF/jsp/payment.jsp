<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="s" value="${booking.snapshot}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Simulated payment</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>SIMULATED PAYMENT</h1>
<p><strong>This is a simulation. No money is charged, no payment provider is contacted, and no card or account details are asked for or stored.</strong></p>
<p>Booking <c:out value="${booking.bookingId}"/>: <c:out value="${s.routeName}"/> (route <c:out value="${s.routeId}"/>),
    <c:out value="${s.source}"/> &rarr; <c:out value="${s.destination}"/></p>
<p>Amount from the booking snapshot: <strong>INR <c:out value="${s.totalCost}"/></strong></p>
<form method="post" action="<c:url value='/payments'/>">
    <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
    <input type="hidden" name="bookingId" value="<c:out value='${booking.bookingId}'/>">
    <p>
        <label for="method">Method (label only)</label><br>
        <select id="method" name="method" required>
            <c:forEach var="m" items="${methods}">
                <option value="<c:out value='${m}'/>"><c:out value="${m.label}"/></option>
            </c:forEach>
        </select>
    </p>
    <p>
        <button type="submit" name="outcome" value="<c:out value='${successOutcome}'/>">Simulate success</button>
        <button type="submit" name="outcome" value="<c:out value='${failureOutcome}'/>">Simulate failure</button>
    </p>
</form>
<c:url var="backUrl" value="/bookings/view"><c:param name="id" value="${booking.bookingId}"/></c:url>
<p><a href="<c:out value='${backUrl}'/>">Back to the booking</a></p>
</body>
</html>
