/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableutils.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalGroup;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityException;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityRef;
import org.apache.jackrabbit.oak.spi.security.authentication.external.basic.DefaultSyncConfig;
import org.apache.jackrabbit.oak.spi.security.authentication.external.basic.DefaultSyncContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

@Service(ExternalGroupCreatorServiceImpl.class)
@Component(metatype = false)
public class ExternalGroupCreatorServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalGroupCreatorServiceImpl.class);

    public Authorizable createGroupWithExternalId(
            final UserManager userManager,
            final AuthorizableConfigBean authorizableConfigBean,
            AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            ValueFactory vf,
            Map<String, Set<AuthorizableConfigBean>> principalMapFromConfig, Session session)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {

        if (StringUtils.isBlank(authorizableConfigBean.getExternalId())) {
            throw new IllegalStateException("externalId must not be emtpy for " + authorizableConfigBean);
        }

        ExternalGroup externalGroup = new PrecreatedExternalGroup(authorizableConfigBean);

        ExternalGroupPrecreatorSyncContext externalGroupPrecreatorSyncContext = new ExternalGroupPrecreatorSyncContext(userManager, vf);
        Group group = externalGroupPrecreatorSyncContext.createExternalGroup(externalGroup);

        return group;
    }

    // simple workaround to make protected method available here
    private final class ExternalGroupPrecreatorSyncContext extends DefaultSyncContext {

        private ExternalGroupPrecreatorSyncContext(UserManager userManager, ValueFactory valueFactory) {
            super(new DefaultSyncConfig(), null, userManager, valueFactory);
        }

        private Group createExternalGroup(ExternalGroup eg) throws RepositoryException {
            config.group().setPathPrefix("");
            return createGroup(eg);
        }
    }

    // mapping AuthorizableConfigBean -> ExternalGroup
    private final class PrecreatedExternalGroup implements ExternalGroup {
        private final AuthorizableConfigBean authorizableConfigBean;

        private PrecreatedExternalGroup(AuthorizableConfigBean authorizableConfigBean) {
            this.authorizableConfigBean = authorizableConfigBean;
        }

        @Override
        public String getId() {
            return authorizableConfigBean.getPrincipalID();
        }

        @Override
        public String getPrincipalName() {
            String principalName = ExternalIdentityRef.fromString(authorizableConfigBean.getExternalId()).getId();
            return principalName;
        }

        @Override
        public String getIntermediatePath() {
            String rawIntermediatePath = authorizableConfigBean.getPath();
            String intermediatePath = StringUtils.removeStart(rawIntermediatePath, Constants.GROUPS_ROOT + "/");
            return intermediatePath;
        }

        @Override
        public ExternalIdentityRef getExternalId() {
            return ExternalIdentityRef.fromString(authorizableConfigBean.getExternalId());
        }

        @Override
        public Map<String, ?> getProperties() {
            return Collections.<String, Object> emptyMap();
        }

        @Override
        public Iterable<ExternalIdentityRef> getDeclaredGroups() throws ExternalIdentityException {
            return Collections.<ExternalIdentityRef> emptyList();
        }

        @Override
        public Iterable<ExternalIdentityRef> getDeclaredMembers() throws ExternalIdentityException {
            return Collections.<ExternalIdentityRef> emptyList();
        }
    }

}
