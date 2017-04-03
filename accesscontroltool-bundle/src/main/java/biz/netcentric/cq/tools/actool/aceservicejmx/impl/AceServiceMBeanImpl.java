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

import javax.jcr.RepositoryException;
import javax.jcr.Session;
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

import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.aceservicejmx.AceServiceMBean;
import biz.netcentric.cq.tools.actool.dumpservice.Dumpservice;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.installationhistory.AcHistoryService;

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

    @Reference
    SlingRepository repository;

    @Override
    public String apply() {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            return aceService.execute(session).toString();
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public String applyRestrictedToPaths(String paths) {

        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            String[] restrictedToPaths = commaSeparatedStringToArr(paths);
            return aceService.execute(restrictedToPaths, session).toString();
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public String apply(String configurationRootPath) {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            return aceService.execute(configurationRootPath, session).toString();
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public String applyRestrictedToPaths(String configurationRootPath, String paths) {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            String[] restrictedToPaths = commaSeparatedStringToArr(paths);
            return aceService.execute(configurationRootPath, restrictedToPaths, session).toString();
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public boolean isReadyToStart() {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            return aceService.isReadyToStart(session);
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return false;
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public String purgeACL(final String path) {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            return aceService.purgeACL(path, session);
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public String purgeACLs(final String path) {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            return aceService.purgeACLs(path, session);
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public boolean isExecuting() {
        return aceService.isExecuting();
    }

    @Override
    public String[] getConfigurationFiles() {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            final Set<String> paths = aceService.getCurrentConfigurationPaths(session);
            StringBuilder sb = new StringBuilder();
            int cnt = 1;
            for (String path : paths) {
                sb.append(cnt + ". " + path + " \n");
                cnt++;
            }

            return paths.toArray(new String[paths.size()]);

        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return new String[] { "An error has ocurred. See logs for details" };
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public String[] getSavedLogs() {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            String[] logs = acHistoryService.getInstallationLogPaths(session);
            if (logs.length == 0) {
                return new String[] { "no logs found" };
            }
            return logs;
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return new String[] { "An error has ocurred. See logs for details" };
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public String pathBasedDump() {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            StopWatch sw = new StopWatch();
            sw.start();
            String dump = dumpservice.getCompletePathBasedDumpsAsString(session);
            sw.stop();
            LOG.info("path based dump took: " + sw.getTime() + " ms");
            return dump;
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public String groupBasedDump() {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            StopWatch sw = new StopWatch();
            sw.start();
            String dump = dumpservice.getCompletePrincipalBasedDumpsAsString(session);
            sw.stop();
            LOG.info("group based dump took: " + sw.getTime() + " ms");
            return dump;
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public String showHistoryLog(final String n) {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            int i;
            String[] logs = acHistoryService.getInstallationLogPaths(session);
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
            return acHistoryService.showHistory(i, session);
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    @Override
    public String purgeAllAuthorizablesFromConfiguration() {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            return aceService.purgeAuthorizablesFromConfig(session);
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public String purgeAuthorizables(String authorizableIds) {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            return aceService.purgeAuthorizables(commaSeparatedStringToArr(authorizableIds), session);
        } catch (RepositoryException e) {
            LOG.error("error executing jmx call", e);
            return "An error has ocurred. See logs for details";
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    private String[] commaSeparatedStringToArr(String str) {
        String[] restrictedToPaths = null;
        if (StringUtils.isNotBlank(str)) {
            restrictedToPaths = str.trim().split("[ \t]*,[ \t]*");
        }
        return restrictedToPaths;
    }

}
