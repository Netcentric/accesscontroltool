/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceservicejmx.impl;

import java.util.Set;

import javax.management.NotCompliantMBeanException;

import org.apache.commons.lang.time.StopWatch;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.aceservicejmx.AceServiceMBean;
import biz.netcentric.cq.tools.actool.dumpservice.Dumpservice;
import biz.netcentric.cq.tools.actool.installationhistory.AcHistoryService;

import com.adobe.granite.jmx.annotation.AnnotatedStandardMBean;

@Service
@Component(immediate = true)
@Properties({
        @Property(name = "jmx.objectname", value = "biz.netcentric.cq.tools:type=ACTool"),
        @Property(name = "pattern", value = "/.*") })
public class AceServiceMBeanImpl extends AnnotatedStandardMBean implements
        AceServiceMBean {

    public AceServiceMBeanImpl() throws NotCompliantMBeanException {
        super(AceServiceMBean.class);
    }

    private static final Logger LOG = LoggerFactory
            .getLogger(AceServiceMBeanImpl.class);

    @Reference
    AceService aceService;

    @Reference
    AcHistoryService acHistoryService;

    @Reference
    Dumpservice dumpservice;

    @Override
    public String execute() {
        return aceService.execute().toString();
    }

    @Override
    public boolean isReadyToStart() {
        return aceService.isReadyToStart();
    }

    @Override
    public String purgeACL(final String path) {
        return aceService.purgeACL(path);
    }

    @Override
    public String purgeACLs(final String path) {
        return aceService.purgeACLs(path);
    }

    @Override
    public boolean isExecuting() {
        return aceService.isExecuting();
    }

    @Override
    public String[] getConfigurationFiles() {
        final Set<String> paths = aceService.getCurrentConfigurationPaths();
        StringBuilder sb = new StringBuilder();
        int cnt = 1;
        for (String path : paths) {
            sb.append(cnt + ". " + path + " \n");
            cnt++;
        }

        return paths.toArray(new String[paths.size()]);

    }

    @Override
    public String[] getSavedLogs() {
        String[] logs = acHistoryService.getInstallationLogPaths();
        if (logs.length == 0) {
            return new String[] { "no logs found" };
        }
        return logs;
    }

    @Override
    public String pathBasedDump() {
        StopWatch sw = new StopWatch();
        sw.start();
        String dump = dumpservice.getCompletePathBasedDumpsAsString();
        sw.stop();
        LOG.info("path based dump took: " + sw.getTime() + " ms");
        return dump;
    }

    @Override
    public String groupBasedDump() {
        StopWatch sw = new StopWatch();
        sw.start();
        String dump = dumpservice.getCompletePrincipalBasedDumpsAsString();
        sw.stop();
        LOG.info("group based dump took: " + sw.getTime() + " ms");
        return dump;
    }

    @Override
    public String showHistoryLog(final String n) {
        int i;
        String[] logs = acHistoryService.getInstallationLogPaths();
        if (logs.length == 0) {
            return "no logs found";
        }
        int numberOfFoundLogs = logs.length;

        String errorMessage = "please enter a valid log number (between 1 and "
                + numberOfFoundLogs + ")";
        try {
            i = Integer.parseInt(n);
        } catch (NumberFormatException e) {
            return errorMessage;
        }
        if (i < 1 || i > numberOfFoundLogs) {
            return errorMessage;
        }
        return acHistoryService.showHistory(i);
    }

    @Override
    public String purgeAllAuthorizablesFromConfigurations() {
        return aceService.purgeAuthorizablesFromConfig();
    }

    @Override
    public String purgeAuthorizables(String authorizableIds) {
        return aceService.purgeAuthorizables(authorizableIds);
    }

}
