<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <c:if test="${not job.finished}"><meta http-equiv="refresh" content="2"></c:if>
    <title>LogiOpt - Report status</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>Report status</h1>
<p>Job <c:out value="${job.jobId}"/> for shipment <c:out value="${job.shipmentId}"/>, strategy <c:out value="${job.strategyKey}"/></p>
<p>Status: <strong><c:out value="${job.status}"/></strong></p>
<c:if test="${not job.finished}"><p>This page refreshes every 2 seconds until the report is finished.</p></c:if>
<p>Requested <c:out value="${job.createdAt}"/><c:if test="${not empty job.finishedAt}">, finished <c:out value="${job.finishedAt}"/></c:if></p>
<c:if test="${job.done}">
    <c:url var="downloadUrl" value="/reports/download"><c:param name="jobId" value="${job.jobId}"/></c:url>
    <p><a href="<c:out value='${downloadUrl}'/>">Download the report (TXT)</a></p>
</c:if>
<c:if test="${not empty job.errorMessage}"><p><c:out value="${job.errorMessage}"/></p></c:if>
<c:url var="backUrl" value="/compare">
    <c:param name="shipmentId" value="${job.shipmentId}"/>
    <c:param name="strategy" value="${job.strategyKey}"/>
</c:url>
<p><a href="<c:out value='${backUrl}'/>">Back to the comparison</a> | <a href="<c:url value='/history'/>">History (all reports)</a></p>
</body>
</html>
