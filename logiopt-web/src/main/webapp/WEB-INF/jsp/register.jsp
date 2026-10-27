<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Register</title>
</head>
<body>
<h1>Create a LogiOpt account</h1>
<c:if test="${not empty errors}">
    <h2>Please correct the following</h2>
    <ul>
        <c:forEach var="error" items="${errors}">
            <li><c:out value="${error}"/></li>
        </c:forEach>
    </ul>
</c:if>
<form method="post" action="<c:url value='/register'/>">
    <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
    <p>
        <label for="email">Email</label><br>
        <input type="email" id="email" name="email" maxlength="255" required autocomplete="email"
               value="<c:out value='${email}'/>">
    </p>
    <p>
        <label for="name">Name</label><br>
        <input type="text" id="name" name="name" maxlength="100" required autocomplete="name"
               value="<c:out value='${name}'/>">
    </p>
    <p>
        <label for="password">Password (at least 8 characters)</label><br>
        <input type="password" id="password" name="password" minlength="8" maxlength="128" required
               autocomplete="new-password">
    </p>
    <p><button type="submit">Register</button></p>
</form>
<p>Already registered? <a href="<c:url value='/login'/>">Sign in</a></p>
</body>
</html>
