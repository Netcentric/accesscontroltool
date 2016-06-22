/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.dumpservice;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AceBean;

public class AcDumpElementYamlVisitor implements AcDumpElementVisitor {

    private final static int PRINCIPAL_BASED_SORTING = 1;
    private final static int PATH_BASED_SORTING = 2;

    public static final int DUMP_INDENTATION_KEY = 4;
    public static final int DUMP_INDENTATION_FIRST_PROPERTY = 7;
    public static final int DUMP_INDENTATION_PROPERTY = 9;
    public static final int DUMP_INDENTATION_RESTRICTIONS = 11;

    public static final String YAML_STRUCTURAL_ELEMENT_PREFIX = "- ";
    private final StringBuilder sb;
    private final int mapOrder;

    public AcDumpElementYamlVisitor(final int mapOrder, final StringBuilder sb) {
        this.mapOrder = mapOrder;
        this.sb = sb;
    }

    @Override
    public void visit(final AuthorizableConfigBean authorizableConfigBean) {
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_KEY))
        .append("- " + authorizableConfigBean.getPrincipalID() + ":")
        .append("\n");
        sb.append("\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY))
        .append("- name: ").append("\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
        .append("memberOf: "
                + authorizableConfigBean.getMemberOfString())
        .append("\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
        .append("path: " + authorizableConfigBean.getPath())
        .append("\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
        .append("isGroup: " + "'" + authorizableConfigBean.isGroup()
        + "'").append("\n");
        sb.append("\n");
    }

    @Override
    public void visit(final AceBean aceBean) {

        if (mapOrder == PATH_BASED_SORTING) {
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY))
            .append("- principal: " + aceBean.getPrincipalName())
            .append("\n");
        } else if (mapOrder == PRINCIPAL_BASED_SORTING) {
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY))
            .append("- path: " + aceBean.getJcrPath()).append("\n");
        }
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
        .append("permission: " + aceBean.getPermission()).append("\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
        .append("actions: " + aceBean.getActionsString()).append("\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
        .append("privileges: " + aceBean.getPrivilegesString())
        .append("\n");
        writeRestrictions(aceBean, sb);
        sb.append("\n");
        sb.append("\n");
    }

    private void writeRestrictions(final AceBean aceBean, final StringBuilder sb){
        final Map<String, List<String>> restrictions = aceBean.getRestrictions();
        if(restrictions.isEmpty()){
            return;
        }
        for(final String restrictionName : restrictions.keySet()){
            final String restrictionsValueString = StringUtils.join(restrictions.get(restrictionName), ",");
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append(restrictionName).append(": ").append(restrictionsValueString);
            sb.append("\n");
        }
    }

    @Override
    public void visit(final CommentingDumpElement commentingDumpElement) {
        sb.append(CommentingDumpElement.YAML_COMMENT_PREFIX
                + commentingDumpElement.getString());
        sb.append("\n");
        sb.append("\n");
    }

    @Override
    public void visit(final StructuralDumpElement structuralDumpElement) {
        sb.append("\n");
        sb.append(AcHelper.getBlankString(structuralDumpElement.getLevel() * 2)
                + YAML_STRUCTURAL_ELEMENT_PREFIX
                + structuralDumpElement.getString()
                + MapKey.YAML_MAP_KEY_SUFFIX);
        sb.append("\n");
        sb.append("\n");
    }
}
