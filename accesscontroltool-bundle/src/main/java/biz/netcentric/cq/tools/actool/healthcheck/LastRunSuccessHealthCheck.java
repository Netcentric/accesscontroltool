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

import org.apache.sling.hc.api.HealthCheck;
import org.apache.sling.hc.api.Result;
import org.apache.sling.hc.util.FormattingResultLog;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.impl.HistoryUtils;

/** Sling Health Check that returns WARN if the last installation failed. */
@Component
@Designate(ocd=LastRunSuccessHealthCheck.Configuration.class)
public class LastRunSuccessHealthCheck implements HealthCheck {

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcHistoryService historyService;

    @ObjectClassDefinition(name = "Sling Health Check: Last Run of AC Tool", 
            description="Health Check Configuration",
            id="biz.netcentric.cq.tools.actool.healthcheck.LastRunSuccessHealthCheck")
    protected static @interface Configuration {
        @AttributeDefinition(name="Tags", description="Tags")
        String[] hc_tags() default "actool";
        
        @AttributeDefinition(name="Cron expression", description ="Cron expression for asynchronous execution (leave empty for synchronous execution)")
        String hc_async_cronExpression();
        
        @AttributeDefinition(name="Name", description = "Name")
        String hc_name() default "Last Run of AC Tool";
        
        @AttributeDefinition(name="MBean", description = "MBean name (leave empty for not using JMX)")
        String hc_mbean_name() default "";
    }
    
    @Override
    public Result execute() {
        final FormattingResultLog resultLog = new FormattingResultLog();

        if (!historyService.wasLastPersistHistoryCallSuccessful()) {
            resultLog.warn("Last execution of AC Tool could not persist its history. Check the log file for details.");
            resultLog.info("Details about persisted history:");
        }

        Session session = null;

        try {
            session = repository.loginService(null, null);

            Node statisticsRootNode = HistoryUtils.getAcHistoryRootNode(session);
            NodeIterator it = statisticsRootNode.getNodes();

            Node lastHistoryNode = null;
            while (it.hasNext()) {
                Node nextNode = it.nextNode();
                if (nextNode.getName().startsWith("history")) {
                    lastHistoryNode = nextNode;
                    break;
                }
            }

            if (lastHistoryNode != null) {

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
