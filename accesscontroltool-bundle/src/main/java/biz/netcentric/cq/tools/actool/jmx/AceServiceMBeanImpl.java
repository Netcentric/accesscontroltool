/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.jmx;

import java.util.List;
import java.util.Set;

import javax.management.NotCompliantMBeanException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.jmx.annotation.AnnotatedStandardMBean;

import biz.netcentric.cq.tools.actool.dumpservice.ConfigDumpService;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.AcToolExecution;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceInternal;

@Component(property = {
        "jmx.objectname=biz.netcentric.cq.tools:type=ACTool",
        "pattern=/.*"
})
public class AceServiceMBeanImpl extends AnnotatedStandardMBean implements AceServiceMBean {
    private static final Logger LOG = LoggerFactory.getLogger(AceServiceMBeanImpl.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcInstallationServiceInternal acInstallationService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcHistoryService acHistoryService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    ConfigDumpService dumpService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
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
    public String applyRestrictedToPaths(String configurationRootPath, String paths, boolean skipIfConfigUnchanged) {
        String[] restrictedToPaths = commaSeparatedStringToArr(paths);
        return acInstallationService.apply(configurationRootPath, restrictedToPaths, skipIfConfigUnchanged).toString();
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
        List<AcToolExecution> executions = acHistoryService.getAcToolExecutions();
        if (executions.isEmpty()) {
            return new String[] { "no executions found" };
        }
        return executions.stream().map(AcToolExecution::getLogsPath).toArray(String[]::new);
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
    public String showInstallationLog(final String n, boolean verbose) {
        int i;
        String[] logs = getSavedLogs();
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
        return acHistoryService.getLogFromHistory(i, false, verbose);
    }

    @Override
    public String purgeAllAuthorizablesFromConfiguration() {
        return acInstallationService.purgeAuthorizablesFromConfig();
    }
    @Override
    public String purgeAllAuthorizablesFromConfiguration(String configurationRootPath) {
        return acInstallationService.purgeAuthorizablesFromConfig(configurationRootPath);
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
