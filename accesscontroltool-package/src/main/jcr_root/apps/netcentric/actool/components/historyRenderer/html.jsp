<%--
 (C) Copyright 2015 Netcentric AG.

 All rights reserved. This program and the accompanying materials
 are made available under the terms of the Eclipse Public License v1.0
 which accompanies this distribution, and is available at
 http://www.eclipse.org/legal/epl-v10.html
--%>
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