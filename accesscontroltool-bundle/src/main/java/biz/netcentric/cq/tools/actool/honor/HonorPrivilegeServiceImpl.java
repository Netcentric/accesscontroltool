/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.honor;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.history.AcInstallationLog;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlEntry;
import java.util.HashSet;
import java.util.Set;


/**
 * Implementation of HonorPrivilegeService, providing a means of saving the ACEs defined as honorPaths in the
 * privilege configuration, and restoring these privileges at a later point in time.
 *
 * @author netcentric
 */
@Service
@Component
public class HonorPrivilegeServiceImpl implements HonorPrivilegeService {

    private static final Logger LOG = LoggerFactory.getLogger(HonorPrivilegeServiceImpl.class);

    @Reference
    private SlingRepository repository;

    @Override
    public Set<PathACL> takePrivilegeSnapshot(AuthorizablesConfig authConf, AcInstallationLog installLog) throws RepositoryException {

        Session session = repository.loginAdministrative(null);
        Set<PathACL> result = new HashSet<>();

        for (AuthorizableConfigBean conf : authConf) {
            Set<PathACL> acls = new HashSet<>();
            for (String honorPath : conf.getHonorPaths()) {

                // Attempting to serialise the root node permissions provokes the following error:
                // OakVersion0001: Cannot change property jcr:mixinTypes on checked in node
                if (("/").equals(honorPath)) {
                    String message = "Honor privilege on root folder ignored.";
                    installLog.addWarning(LOG, message);
                } else if (StringUtils.isBlank(honorPath.trim())) {
                    installLog.addError(LOG, "Honor path blank");
                } else {
                    Set<PathACL> found = this.findRecursiveACEs(session, conf.getAuthorizableId(), honorPath, installLog);
                    installLog.addMessage(LOG, found.size() + " ACEs found for user "
                            + conf.getPrincipalName() + " at honor path " + honorPath);
                    acls.addAll(found);
                }
            }
            if (acls.isEmpty()) {
                LOG.debug("No custom ACEs found for group " + conf.getAuthorizableId());
            }

            result.addAll(acls);
        }

        return result;
    }


    /**
     * Note that this implementation DOES NOT SAVE CHANGES. Clients must call session.save() to persist the
     * snapshot.
     */
    @Override
    public void restorePrivilegeSnapshot(Set<PathACL> snapshotACL, AcInstallationLog installLog)
            throws RepositoryException {
        Session session = null;

        try {
            session = repository.loginAdministrative(null);
            for (PathACL pathACL : snapshotACL) {
                AccessControlUtils.applyAccessControlList(session, pathACL.getPath(), pathACL.getAcl());
            }
            if (!snapshotACL.isEmpty()) {
                session.save();
                installLog.addMessage(LOG, snapshotACL.size() + " ACEs from honor paths successfully restored.");
            }
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private Set<PathACL> findRecursiveACEs(Session session, String group, String path, AcInstallationLog installLog) throws RepositoryException {
        Set<PathACL> result = new HashSet<>();


        try {
            if (!path.startsWith("/")) {
                installLog.addWarning(LOG, "Honor path " + path + " is not absolute - ignoring.");
            } else {
                Set<String> paths = this.findSubPaths(session.getNode(path));

                for (String absPath : paths) {
                    // Restricted to given group.
                    JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(session, absPath);

                    acl = this.filterPoliciesByGroup(acl, group);
                    // Ignore ACLs with access rules
                    if (acl.getAccessControlEntries().length > 0) {
                        result.add(new PathACL(absPath, this.filterPoliciesByGroup(acl, group)));
                        LOG.debug("Found ACL for group " + group + " on path " + absPath + ": " + acl);
                    }
                }
            }
        } catch (PathNotFoundException e) {
            String msg = "Honor path " + path + " not found: ignoring.";
            installLog.addWarning(LOG, msg);
        }

        return result;
    }

    private Set<String> findSubPaths(Node node) throws RepositoryException {
        Set<String> result = new HashSet<>();
        for (Node subNode : JcrUtils.getChildNodes(node)) {
            result.addAll(this.findSubPaths(subNode));
        }
        // Ensure that node is not meta-data
        if (node.getName().indexOf(':') < 0 && node.getPath().indexOf(':') < 0) {
            result.add(node.getPath());
        }
        return result;
    }


    private JackrabbitAccessControlList filterPoliciesByGroup(JackrabbitAccessControlList acl, String group)
            throws RepositoryException {
        for (AccessControlEntry entry : acl.getAccessControlEntries()) {
            if (!entry.getPrincipal().getName().equals(group)) {
                acl.removeAccessControlEntry(entry);
            }
        }
        return acl;
    }
}
