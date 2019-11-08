/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import biz.netcentric.cq.tools.actool.authorizableinstaller.PrivilegeInstaller;
import biz.netcentric.cq.tools.actool.configmodel.PrivilegeBean;
import biz.netcentric.cq.tools.actool.configmodel.PrivilegeConfig;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;
import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.apache.jackrabbit.api.security.authorization.PrivilegeManager;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.lang.invoke.MethodHandles;

@Component
public class PrivilegeInstallerImpl implements PrivilegeInstaller {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public void installPrivileges(PrivilegeConfig config, Session session, InstallationLogger installLog) throws RepositoryException {
        PrivilegeManager privilegeManager = ((JackrabbitWorkspace) session.getWorkspace()).getPrivilegeManager();
        for (PrivilegeBean privilege : config) {
            String privilegeName = privilege.getPrivilegeName();
            try {
                privilegeManager.registerPrivilege(
                        privilegeName,
                        privilege.isAbstract(),
                        privilege.getAggregateNames());
                installLog.addMessage(LOG, "created " + privilegeName);
            } catch (RepositoryException e) {
                String alreadyExistsMsg = "Privilege definition with name '" + privilegeName + "' already exists.";
                if (alreadyExistsMsg.equals(e.getMessage())) {
                    installLog.addMessage(LOG, alreadyExistsMsg);
                } else {
                    throw e;
                }
            }
        }

    }
}
