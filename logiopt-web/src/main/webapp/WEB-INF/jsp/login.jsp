<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>LogiOpt - Sign in</title>
</head>
<body>
<h1>Sign in to LogiOpt</h1>
<c:if test="${not empty notice}"><p><strong><c:out value="${notice}"/></strong></p></c:if>
<c:if test="${not empty error}"><p><strong><c:out value="${error}"/></strong></p></c:if>
<form method="post" action="<c:url value='/login'/>">
    <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
    <p>
        <label for="email">Email</label><br>
        <input type="email" id="email" name="email" maxlength="255" required autocomplete="username"
               value="<c:out value='${email}'/>">
    </p>
    <p>
        <label for="password">Password</label><br>
        <input type="password" id="password" name="password" maxlength="128" required autocomplete="current-password">
    </p>
    <p><button type="submit">Sign in</button></p>
</form>
<p>No account yet? <a href="<c:url value='/register'/>">Register</a></p>
</body>
</html>
