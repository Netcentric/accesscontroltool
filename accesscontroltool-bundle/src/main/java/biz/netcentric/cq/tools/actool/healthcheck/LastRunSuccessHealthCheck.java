/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.healthcheck;

import java.util.Date;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.hc.annotations.SlingHealthCheck;
import org.apache.sling.hc.api.HealthCheck;
import org.apache.sling.hc.api.Result;
import org.apache.sling.hc.util.FormattingResultLog;
import org.apache.sling.jcr.api.SlingRepository;

import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.history.impl.HistoryUtils;

/** Sling Health Check that returns WARN if the last installation failed. */
@SlingHealthCheck(name = "Last Run of AC Tool", tags = "actool")
public class LastRunSuccessHealthCheck implements HealthCheck {

    @Reference
    private SlingRepository repository;

    @Override
    public Result execute() {
        final FormattingResultLog resultLog = new FormattingResultLog();

        Session session = null;

        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);

            Node statisticsRootNode = HistoryUtils.getAcHistoryRootNode(session);
            NodeIterator it = statisticsRootNode.getNodes();

            if (it.hasNext()) {
                Node lastHistoryNode = it.nextNode();
                boolean isSuccess = lastHistoryNode.getProperty(HistoryUtils.PROPERTY_SUCCESS).getBoolean() == true;
                String installedFrom = lastHistoryNode.getProperty(HistoryUtils.PROPERTY_INSTALLED_FROM).getString();
                Long installationTime = lastHistoryNode.getProperty(HistoryUtils.PROPERTY_TIMESTAMP).getLong();
                String msg = (isSuccess ? "Success" : "Failed") + " for " + installedFrom + " ("
                        + new Date(installationTime) + ")";
                if (isSuccess) {
                    resultLog.info(msg);
                } else {
                    resultLog.warn(msg);
                }
            } else {
                resultLog.info("No AC Tool History entries exist");
            }
        } catch (RepositoryException e) {
            return new Result(Result.Status.HEALTH_CHECK_ERROR, "Error while retrieving last AC Tool runs", e);
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
        return new Result(resultLog);
    }
}
