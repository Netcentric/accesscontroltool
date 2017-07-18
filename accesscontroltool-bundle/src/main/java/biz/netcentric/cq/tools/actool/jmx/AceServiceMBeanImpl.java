/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.jmx;

import java.util.Set;

import javax.management.NotCompliantMBeanException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.jmx.annotation.AnnotatedStandardMBean;

import biz.netcentric.cq.tools.actool.dumpservice.ConfigDumpService;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceInternal;

@Service
@Component(immediate = true)
@Properties({
        @Property(name = "jmx.objectname", value = "biz.netcentric.cq.tools:type=ACTool"),
        @Property(name = "pattern", value = "/.*") })
public class AceServiceMBeanImpl extends AnnotatedStandardMBean implements AceServiceMBean {
    private static final Logger LOG = LoggerFactory.getLogger(AceServiceMBeanImpl.class);

    @Reference
    AcInstallationServiceInternal acInstallationService;

    @Reference
    AcHistoryService acHistoryService;

    @Reference
    ConfigDumpService dumpService;

    @Reference
    SlingRepository repository;

    public AceServiceMBeanImpl() throws NotCompliantMBeanException {
        super(AceServiceMBean.class);
    }

    @Override
    public String apply() {
        return acInstallationService.apply().toString();
    }

    @Override
    public String applyRestrictedToPaths(String paths) {
        String[] restrictedToPaths = commaSeparatedStringToArr(paths);
        return acInstallationService.apply(restrictedToPaths).toString();
    }

    @Override
    public String apply(String configurationRootPath) {
        return acInstallationService.apply(configurationRootPath).toString();
    }

    @Override
    public String applyRestrictedToPaths(String configurationRootPath, String paths) {
        String[] restrictedToPaths = commaSeparatedStringToArr(paths);
        return acInstallationService.apply(configurationRootPath, restrictedToPaths).toString();
    }

    @Override
    public String purgeACL(final String path) {
        return acInstallationService.purgeACL(path);
    }

    @Override
    public String purgeACLs(final String path) {
        return acInstallationService.purgeACLs(path);
    }

    @Override
    public String[] getConfigurationFiles() {
        final Set<String> paths = acInstallationService.getCurrentConfigurationPaths();
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
        String dump = dumpService.getCompletePathBasedDumpsAsString();
        sw.stop();
        LOG.info("path based dump took: " + sw.getTime() + " ms");
        return dump;
    }

    @Override
    public String groupBasedDump() {
        StopWatch sw = new StopWatch();
        sw.start();
        String dump = dumpService.getCompletePrincipalBasedDumpsAsString();
        sw.stop();
        LOG.info("group based dump took: " + sw.getTime() + " ms");
        return dump;
    }

    @Override
    public String showInstallationLog(final String n) {
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
    public String purgeAllAuthorizablesFromConfiguration() {
        return acInstallationService.purgeAuthorizablesFromConfig();
    }

    @Override
    public String purgeAuthorizables(String authorizableIds) {
        return acInstallationService.purgeAuthorizables(commaSeparatedStringToArr(authorizableIds));
    }

    private String[] commaSeparatedStringToArr(String str) {
        String[] restrictedToPaths = null;
        if (StringUtils.isNotBlank(str)) {
            restrictedToPaths = str.trim().split("[ \t]*,[ \t]*");
        }
        return restrictedToPaths;
    }

    @Override
    public String getVersion() {
        return acInstallationService.getVersion();
    }

}
