/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.api.security.user.Impersonation;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

/** Installs impersonators to users. */
@Component(service = ImpersonationInstallerServiceImpl.class)
public class ImpersonationInstallerServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(ImpersonationInstallerServiceImpl.class);

    public void setupImpersonation(User user, List<String> impersonationAllowedFor, AuthorizablesConfig authorizablesConfig,
            InstallationLogger installationLog) {

        try {
            List<String> impersonatorsToBeSet = expandImpersonatorsList(user.getID(), impersonationAllowedFor, authorizablesConfig);
            Impersonation impersonation = user.getImpersonation();
            List<String> currentImpersonators = collectCurrentImpersonators(impersonation);
            applyChanges(user, installationLog, impersonatorsToBeSet, impersonation, currentImpersonators);
        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not setup impersonation for user " + user + ": " + e, e);
        }
    }

    private void applyChanges(User user, InstallationLogger installationLog, List<String> impersonatorsToBeSet, Impersonation impersonation,
            List<String> currentImpersonators) throws RepositoryException {
        List<String> impersonatorsToAdd = new ArrayList<>();
        impersonatorsToAdd.addAll(impersonatorsToBeSet);
        impersonatorsToAdd.removeAll(currentImpersonators);
        Iterator<String> impersonatorsToAddIt = impersonatorsToAdd.iterator();
        while (impersonatorsToAddIt.hasNext()) {
            String impersonatorToAdd = impersonatorsToAddIt.next();
            boolean success = impersonation.grantImpersonation(new PrincipalImpl(impersonatorToAdd));
            if (!success) {
                impersonatorsToAddIt.remove();
                installationLog.addWarning(LOG, "Impersonator '" + impersonatorToAdd + "' can not be added to user " + user);
            }
        }

        List<String> impersonatorsToRemove = new ArrayList<>();
        impersonatorsToRemove.addAll(currentImpersonators);
        impersonatorsToRemove.removeAll(impersonatorsToBeSet);
        Iterator<String> impersonatorsToRemoveIt = impersonatorsToRemove.iterator();
        while (impersonatorsToRemoveIt.hasNext()) {
            String impersonatorToRemove = impersonatorsToRemoveIt.next();
            boolean success = impersonation.revokeImpersonation(new PrincipalImpl(impersonatorToRemove));
            if (!success) {
                impersonatorsToRemoveIt.remove();
                installationLog.addWarning(LOG, "Impersonator '" + impersonatorToRemove + "' can not be removed from user " + user);
            }
        }

        if (!impersonatorsToAdd.isEmpty() || !impersonatorsToRemove.isEmpty()) {
            installationLog.addVerboseMessage(LOG, "Adjusted impersonation for user " + user.getID() + ":");
            if (!impersonatorsToAdd.isEmpty()) {
                installationLog.addVerboseMessage(LOG, " Added impersonators=" + impersonatorsToAdd);
            }
            if (!impersonatorsToRemove.isEmpty()) {
                installationLog.addVerboseMessage(LOG, " Removed impersonators=" + impersonatorsToRemove);
            }
        }
    }

    private List<String> collectCurrentImpersonators(Impersonation impersonation) throws RepositoryException {
        PrincipalIterator principalIterator = impersonation.getImpersonators();

        List<String> impersonatorsCurrent = new ArrayList<>();
        while (principalIterator.hasNext()) {
            impersonatorsCurrent.add(principalIterator.nextPrincipal().getName());
        }
        return impersonatorsCurrent;
    }

    private List<String> expandImpersonatorsList(String userAuthorizableId, List<String> impersonationAllowedFor,
            AuthorizablesConfig authorizablesConfig) {
        List<String> impersonatorsToBeSet = new ArrayList<>();
        for (String impersonationAllowedForItem : impersonationAllowedFor) {
            if (StringUtils.indexOfAny(impersonationAllowedForItem, '*', '?', '(', '|') > -1) {
                // regex: search over all users in config
                for (AuthorizableConfigBean authorizableConfigBean : authorizablesConfig) {
                    if (!authorizableConfigBean.isGroup()
                            && authorizableConfigBean.getPrincipalName().matches(impersonationAllowedForItem)
                            && !authorizableConfigBean.getAuthorizableId().equals(userAuthorizableId)) {
                        impersonatorsToBeSet.add(authorizableConfigBean.getPrincipalName());
                    }
                }
            } else {
                // literal: use as is
                impersonatorsToBeSet.add(impersonationAllowedForItem);
            }
        }
        return impersonatorsToBeSet;
    }

}
