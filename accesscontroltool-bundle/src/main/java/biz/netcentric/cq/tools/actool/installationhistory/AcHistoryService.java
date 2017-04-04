/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.installationhistory;

import javax.jcr.Session;

public interface AcHistoryService {

    public void persistHistory(AcInstallationHistoryPojo history);

    public void persistAcePurgeHistory(AcInstallationHistoryPojo history);

    public String[] getInstallationLogPaths();

    public String getLogHtml(Session session, String path);

    public String getLogTxt(Session session, String path);

    public String getLastInstallationHistory();

    public String showHistory(int n);

}
