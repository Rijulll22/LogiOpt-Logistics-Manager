<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Shipments</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>My shipments</h1>
<p><a href="<c:url value='/shipments/new'/>">Create a new shipment</a></p>
<c:choose>
    <c:when test="${empty shipments}">
        <p>No shipments yet.</p>
    </c:when>
    <c:otherwise>
        <table border="1" cellpadding="4">
            <thead>
            <tr>
                <th>ID</th><th>Source</th><th>Destination</th><th>Cargo</th><th>Weight (kg)</th>
                <th>Dimensions</th><th>Dispatch date</th><th>Status</th><th>Routes</th><th>Edit</th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="s" items="${shipments}">
                <c:url var="compareUrl" value="/compare">
                    <c:param name="shipmentId" value="${s.shipmentId}"/>
                    <c:param name="strategy" value="${defaultStrategy}"/>
                </c:url>
                <tr>
                    <td><c:out value="${s.shipmentId}"/></td>
                    <td><c:out value="${s.source}"/></td>
                    <td><c:out value="${s.destination}"/></td>
                    <td><c:out value="${s.cargoType}"/></td>
                    <td><c:out value="${s.weightKg}"/></td>
                    <td><c:out value="${s.dimensions}"/></td>
                    <td><c:out value="${s.dispatchDate}"/></td>
                    <td><c:out value="${s.status}"/></td>
                    <td><a href="<c:out value='${compareUrl}'/>">Compare</a></td>
                    <td><c:if test="${s.status == 'DRAFT'}"><c:url var="editUrl" value="/shipments/edit"><c:param name="shipmentId" value="${s.shipmentId}"/></c:url><a href="<c:out value='${editUrl}'/>">Edit draft</a></c:if></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </c:otherwise>
</c:choose>
</body>
</html>
