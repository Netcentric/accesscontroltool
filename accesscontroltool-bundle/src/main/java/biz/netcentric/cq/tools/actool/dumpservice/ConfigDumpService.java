/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.dumpservice;

import java.io.IOException;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AclBean;

public interface ConfigDumpService {

    public Set<AuthorizableConfigBean> getGroupBeans(Session session)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException;

    public boolean isIncludeUsers();

    /** returns the paths under jcr:root which are excluded from search for rep:policy nodes in OSGi configuration
     *
     * @return String array containing the paths */
    public String[] getQueryExcludePaths();

    public String getConfigurationDumpAsString(final AceDumpData aceDumpData,
            final Set<AuthorizableConfigBean> groupSet,
            final Set<AuthorizableConfigBean> userSet, final int mapOrder) throws IOException;

    public Set<AclBean> getACLDumpBeans(final Session session)
            throws RepositoryException;

    /** returns a Map with holds either principal or path based ACE data
     *
     * @param keyOrder either principals (AceHelper.PRINCIPAL_BASED_ORDERING) or node paths (AceHelper.PATH_BASED_ORDERING) as keys
     * @param aclOrdering specifies whether the allow and deny ACEs within an ACL should be divided in separate blocks (first deny then
     *            allow)
     * @param isIncludeUsers
     * @param session a jcr session
     * @return AceDumpData */
    public AceDumpData createAclDumpMap(final int keyOrder, final int aclOrdering,
            final String[] excludePaths,
            final boolean isIncludeUsers, Session session) throws RepositoryException;

    /** method that return a dump comprising of all groups and all aces in path based view
     *
     * @return a string comprising the dump information */
    public String getCompletePathBasedDumpsAsString();

    /** method that return a dump comprising of all groups and all aces in principal based view
     *
     * @return a string comprising the dump information */
    public String getCompletePrincipalBasedDumpsAsString();

}
