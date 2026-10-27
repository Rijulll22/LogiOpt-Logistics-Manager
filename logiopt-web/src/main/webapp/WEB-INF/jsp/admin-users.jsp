<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Users</title>
</head>
<body>
<%@ include file="header.jspf" %>
<h1>User accounts</h1>
<table border="1" cellpadding="4">
    <thead>
    <tr><th>ID</th><th>Email</th><th>Name</th><th>Role</th><th>Can sign in</th></tr>
    </thead>
    <tbody>
    <c:forEach var="u" items="${users}">
        <tr>
            <td><c:out value="${u.userId}"/></td>
            <td><c:out value="${u.email}"/></td>
            <td><c:out value="${u.name}"/></td>
            <td><c:out value="${u.role}"/></td>
            <td><c:choose><c:when test="${u.enabled}">yes</c:when><c:otherwise>no</c:otherwise></c:choose></td>
        </tr>
    </c:forEach>
    </tbody>
</table>
</body>
</html>
