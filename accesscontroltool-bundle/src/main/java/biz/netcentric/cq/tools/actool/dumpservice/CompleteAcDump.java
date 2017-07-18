/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.dumpservice;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.Constants;

public class CompleteAcDump implements AcDumpElement {

    private AceDumpData aceDumpData;
    private Set<AuthorizableConfigBean> groupSet;
    private Set<AuthorizableConfigBean> userSet;
    private String dumpComment;

    private ConfigDumpService dumpservice;

    public CompleteAcDump(AceDumpData aceDumpData,
            final Set<AuthorizableConfigBean> groupSet,
            final Set<AuthorizableConfigBean> userSet, final int mapOrder,
            final String dumpComment, ConfigDumpService dumpservice) {
        this.aceDumpData = aceDumpData;
        this.groupSet = groupSet;
        this.userSet = userSet;
        this.dumpComment = dumpComment;
        this.dumpservice = dumpservice;
    }

    @Override
    public void accept(AcDumpElementVisitor acDumpElementVisitor) {
        Map<String, Set<AceBean>> aceMap = aceDumpData.getAceDump();

        // render group section label
        acDumpElementVisitor.visit(new DumpComment(dumpComment));

        // render group section label
        acDumpElementVisitor.visit(new DumpSectionElement(Constants.GROUP_CONFIGURATION_KEY));

        // render groupBeans
        renderAuthorizableBeans(acDumpElementVisitor, groupSet);

        if (dumpservice.isIncludeUsers()) {
            // render user section label
            acDumpElementVisitor.visit(new DumpSectionElement(Constants.USER_CONFIGURATION_KEY));
            // render userBeans
            renderAuthorizableBeans(acDumpElementVisitor, userSet);
        }

        // render ace section label
        acDumpElementVisitor.visit(new DumpSectionElement(Constants.ACE_CONFIGURATION_KEY));

        // render aceBeans
        renderAceBeans(acDumpElementVisitor, aceMap);

    }

    private void renderAuthorizableBeans(
            AcDumpElementVisitor acDumpElementVisitor,
            final Set<AuthorizableConfigBean> authorizableBeans) {
        for (AuthorizableConfigBean authorizableConfigBean : authorizableBeans) {
            authorizableConfigBean.accept(acDumpElementVisitor);
        }
    }

    private void renderAceBeans(AcDumpElementVisitor acDumpElementVisitor,
            Map<String, Set<AceBean>> aceMap) {
        for (Map.Entry<String, Set<AceBean>> entry : aceMap.entrySet()) {
            Set<AceBean> aceBeanSet = entry.getValue();

            String mapKey = entry.getKey();
            
            String comment = null;
            if(((AcDumpElementYamlVisitor)acDumpElementVisitor).getMapOrder() == AcHelper.PRINCIPAL_BASED_ORDER) {
                AceBean aceBean = aceBeanSet.iterator().next();
                if (!StringUtils.equals(aceBean.getPrincipalName(), aceBean.getAuthorizableId())) {
                    mapKey = aceBean.getAuthorizableId();
                    comment = " principalName: " + aceBean.getPrincipalName();
                }
            }
            
            acDumpElementVisitor.visit(new MapKey(mapKey, comment));

            for (AceBean aceBean : aceBeanSet) {
                aceBean.accept(acDumpElementVisitor);
            }
        }
    }
}
