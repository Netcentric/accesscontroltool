<%@include file="/libs/foundation/global.jsp" %>
<jsp:useBean id="comp" scope="page" class="biz.netcentric.cq.tools.actool.components.HistoryRenderer" />
<jsp:setProperty name="comp" property="slingRequest" value="<%= slingRequest %>" />
<html>
<head>
</head>
<body> 
    ${comp.history}  
</body>
</html>