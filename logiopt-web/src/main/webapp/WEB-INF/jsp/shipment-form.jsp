<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - <c:out value="${empty editShipmentId ? 'New shipment' : 'Edit draft shipment'}"/></title>
</head>
<body>
<%@ include file="header.jspf" %>
<c:choose>
    <c:when test="${empty editShipmentId}"><h1>New shipment</h1></c:when>
    <c:otherwise>
        <h1>Edit draft shipment <c:out value="${editShipmentId}"/></h1>
        <p>Only draft shipments can be edited. Existing bookings keep the values they were booked with.</p>
    </c:otherwise>
</c:choose>
<p><a href="<c:url value='/shipments'/>">All shipments</a></p>

<c:if test="${not empty errors}">
    <h2>Please correct the following</h2>
    <ul>
        <c:forEach var="error" items="${errors}">
            <li><c:out value="${error}"/></li>
        </c:forEach>
    </ul>
</c:if>

<c:url var="formAction" value="${empty editShipmentId ? '/shipments/create' : '/shipments/edit'}"/>
<form method="post" action="<c:out value='${formAction}'/>">
    <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
    <c:if test="${not empty editShipmentId}"><input type="hidden" name="shipmentId" value="<c:out value='${editShipmentId}'/>"></c:if>
    <p>
        <label for="source">Source city</label><br>
        <select id="source" name="source" required>
            <option value="">-- choose --</option>
            <c:forEach var="city" items="${cities}">
                <option value="<c:out value='${city}'/>" <c:if test="${city == form.source}">selected</c:if>><c:out value="${city}"/></option>
            </c:forEach>
        </select>
    </p>
    <p>
        <label for="destination">Destination city</label><br>
        <select id="destination" name="destination" required>
            <option value="">-- choose --</option>
            <c:forEach var="city" items="${cities}">
                <option value="<c:out value='${city}'/>" <c:if test="${city == form.destination}">selected</c:if>><c:out value="${city}"/></option>
            </c:forEach>
        </select>
    </p>
    <p>
        <label for="cargoType">Cargo type</label><br>
        <select id="cargoType" name="cargoType" required>
            <option value="">-- choose --</option>
            <c:forEach var="type" items="${cargoTypes}">
                <option value="<c:out value='${type}'/>" <c:if test="${type == form.cargoType}">selected</c:if>><c:out value="${type}"/></option>
            </c:forEach>
        </select>
    </p>
    <p>
        <label for="weightKg">Weight (kg)</label><br>
        <input type="number" id="weightKg" name="weightKg" step="0.001" min="0.001" max="999999999.999" required
               value="<c:out value='${form.weightKg}'/>">
    </p>
    <p>
        <label for="dimensions">Dimensions (optional, e.g. 2 x 1 x 1 m)</label><br>
        <input type="text" id="dimensions" name="dimensions" maxlength="100"
               value="<c:out value='${form.dimensions}'/>">
    </p>
    <p>
        <label for="dispatchDate">Dispatch date</label><br>
        <input type="date" id="dispatchDate" name="dispatchDate" required
               value="<c:out value='${form.dispatchDate}'/>">
    </p>
    <p><button type="submit"><c:out value="${empty editShipmentId ? 'Save and compare routes' : 'Save changes and compare routes'}"/></button></p>
</form>
<p><em>Only cities served by a configured route can be chosen. Illustrative sample routes (not real carrier tariffs) are configured between every pair of the listed cities, in both directions; source and destination must be different cities. Weight: 0.001 to 999,999,999.999 kg, at most 3 decimal places.</em></p>
</body>
</html>
