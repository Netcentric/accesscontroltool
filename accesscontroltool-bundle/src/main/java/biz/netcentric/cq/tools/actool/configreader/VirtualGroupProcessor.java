/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AcesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@Component(service=VirtualGroupProcessor.class)
public class VirtualGroupProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualGroupProcessor.class);

    void flattenGroupTree(AcConfiguration acConfiguration, InstallationLogger logger) {

        List<AuthorizableConfigBean> virtualGroups = getVirtualGroups(acConfiguration);

        AcesConfig aceConfig = acConfiguration.getAceConfig();

        int countAceAdded = 0;
        int countAceRemoved = 0;

        for (AuthorizableConfigBean virtualAutBean : virtualGroups) {

            logger.addVerboseMessage(LOG, "Authorizable bean " + virtualAutBean.getAuthorizableId() + " is virtual");

            if (!ArrayUtils.isEmpty(virtualAutBean.getMembers())) {
                throw new IllegalArgumentException("It is not allowed to define members in virtual groups (offending virtual group: '"
                        + virtualAutBean.getAuthorizableId() + "')");
            }

            List<AuthorizableConfigBean> referencingAuthBeans = getAuthConfigBeansReferencingVirtualGroup(
                    virtualAutBean.getAuthorizableId(), acConfiguration);

            // fix isMemberOf
            adjustIsMemberOf(logger, virtualAutBean, referencingAuthBeans);

            // fix ace beans
            List<AceBean> aceBeansToBeRemoved = new LinkedList<AceBean>();
            List<AceBean> aceBeansToBeAdded = new LinkedList<AceBean>();
            adjustAceBeans(logger, aceConfig, aceBeansToBeRemoved, aceBeansToBeAdded, virtualAutBean,
                    referencingAuthBeans);

            countAceRemoved += aceBeansToBeRemoved.size();
            aceConfig.removeAll(aceBeansToBeRemoved);

            countAceAdded += aceBeansToBeAdded.size();
            aceConfig.addAll(aceBeansToBeAdded);
        }

        if (virtualGroups.isEmpty()) {
            return;
        }

        acConfiguration.getAuthorizablesConfig().removeAll(virtualGroups);
        acConfiguration.setVirtualGroups(virtualGroups);
        acConfiguration.ensureAceBeansHaveCorrectPrincipalNameSet();

        for (AuthorizableConfigBean autBean : acConfiguration.getAuthorizablesConfig()) {
            for (AuthorizableConfigBean virtualGroup : virtualGroups) {
                if (ArrayUtils.contains(autBean.getIsMemberOf(), virtualGroup.getAuthorizableId())) {
                    throw new IllegalStateException(
                            "Group " + autBean + " in isMemberOf still contains " + virtualGroup.getAuthorizableId());
                }
            }
        }

        logger.addMessage(LOG,
                "Processed " + virtualGroups.size() + " virtual groups, replaced " + countAceRemoved
                        + " ACEs of virtual groups with " + countAceAdded + " new ACEs in configuration");

    }

    private List<AuthorizableConfigBean> getAuthConfigBeansReferencingVirtualGroup(String virtualGroupAuthId,
            AcConfiguration acConfiguration) {
        List<AuthorizableConfigBean> referencingBeans = new ArrayList<>();
        for (AuthorizableConfigBean autBean : acConfiguration.getAuthorizablesConfig()) {
            if (ArrayUtils.contains(autBean.getIsMemberOf(), virtualGroupAuthId)) {
                referencingBeans.add(autBean);
            }
        }
        return referencingBeans;
    }

    private void adjustIsMemberOf(InstallationLogger installationLogger,
            AuthorizableConfigBean virtualAutBean, List<AuthorizableConfigBean> referencingAuthBeans) {
        String[] isMemberOf = virtualAutBean.getIsMemberOf();
        List<String> isMemberOfOfVirtualGroup = isMemberOf != null ? Arrays.asList(isMemberOf) : Collections.<String> emptyList();

        if (referencingAuthBeans == null || referencingAuthBeans.isEmpty()) {
            throw new IllegalArgumentException("Virtual group '" + virtualAutBean.getAuthorizableId()
                    + "' is not used in any isMemberOf attribute of other groups, hence it cannot be declared virtual");
        }

        for (AuthorizableConfigBean otherAuthBean : referencingAuthBeans) {
            installationLogger.addVerboseMessage(LOG,
                    "Virtual Group: " + virtualAutBean.getAuthorizableId() + " - Adding groups " + isMemberOfOfVirtualGroup + " to "
                            + otherAuthBean.getAuthorizableId());
            Set<String> adjustedIsMemberOf = new HashSet<String>(Arrays.asList(otherAuthBean.getIsMemberOf()));
            adjustedIsMemberOf.addAll(isMemberOfOfVirtualGroup);
            adjustedIsMemberOf.remove(virtualAutBean.getAuthorizableId());
            otherAuthBean.setIsMemberOf(new ArrayList<String>(adjustedIsMemberOf));
        }

        // remove all references as they are moved now
        virtualAutBean.setIsMemberOf(new String[0]);

    }

    private void adjustAceBeans(InstallationLogger logger, AcesConfig aceConfig, List<AceBean> aceBeansToBeRemoved,
            List<AceBean> aceBeansToBeAdded, AuthorizableConfigBean virtualAutBean, List<AuthorizableConfigBean> referencingAuthBeans) {

        for (AceBean aceBean : aceConfig) {

            if (StringUtils.equals(aceBean.getAuthorizableId(), virtualAutBean.getAuthorizableId())) {
                logger.addVerboseMessage(LOG,
                        "ACE at path " + aceBean.getJcrPath() + " for virtual group " + virtualAutBean.getAuthorizableId());
                aceBeansToBeRemoved.add(aceBean);

                for (AuthorizableConfigBean newAuthBeanInAcl : referencingAuthBeans) {
                    AceBean cloneForAuthConfigBeanUsingIsMemberOf = aceBean.clone();
                    cloneForAuthConfigBeanUsingIsMemberOf.setAuthorizableId(newAuthBeanInAcl.getAuthorizableId());
                    aceBeansToBeAdded.add(cloneForAuthConfigBeanUsingIsMemberOf);
                    logger.addVerboseMessage(LOG, "  Adding clone for authorizable id " + newAuthBeanInAcl.getAuthorizableId()
                            + " replacing " + aceBean.getAuthorizableId());

                }
            }
        }
    }

    public List<AuthorizableConfigBean> getVirtualGroups(AcConfiguration acConfiguration) {
        List<AuthorizableConfigBean> virtualGroups = new ArrayList<>();
        for (AuthorizableConfigBean authBean : acConfiguration.getAuthorizablesConfig()) {
            if (authBean.isVirtual()) {
                if (!authBean.isGroup()) {
                    throw new IllegalArgumentException("\"virtual: true\" can only be set on groups, not on users");
                }
                virtualGroups.add(authBean);
            }
        }
        return virtualGroups;
    }

}
