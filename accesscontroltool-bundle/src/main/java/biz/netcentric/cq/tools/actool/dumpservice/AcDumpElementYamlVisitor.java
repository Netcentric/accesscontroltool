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

import org.apache.commons.lang.StringUtils;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.Restriction;
import biz.netcentric.cq.tools.actool.helper.AcHelper;

public class AcDumpElementYamlVisitor implements AcDumpElementVisitor {

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
                .append("- " + authorizableConfigBean.getAuthorizableId() + ":")
                .append("\n");
        sb.append("\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY))
                .append("- name: '").append(authorizableConfigBean.getName()).append("'\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
                .append("isMemberOf: "
                        + authorizableConfigBean.getMemberOfString())
                .append("\n");
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
                .append("path: " + authorizableConfigBean.getPath())
                .append("\n");

        if (StringUtils.isNotBlank(authorizableConfigBean.getExternalId())) {
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
                    .append("externalId: " + authorizableConfigBean.getExternalId())
                    .append("\n");
        }

        sb.append("\n");
    }

    @Override
    public void visit(final AceBean aceBean) {

        if (mapOrder == AcHelper.PATH_BASED_ORDER) {
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY)).append("- principal: " + aceBean.getPrincipalName());
            if (!StringUtils.equals(aceBean.getPrincipalName(), aceBean.getAuthorizableId())) {
                sb.append(" # authorizableId: ").append(aceBean.getAuthorizableId());
            }
            sb.append("\n");
        } else if (mapOrder == AcHelper.PRINCIPAL_BASED_ORDER) {
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY))
                    .append("- path: " + StringUtils.defaultIfEmpty(aceBean.getJcrPath(), "")).append("\n");
        }

        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
                .append("permission: " + aceBean.getPermission()).append("\n");

        if (StringUtils.isNotBlank(aceBean.getActionsString())) {
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
                    .append("actions: " + aceBean.getActionsString()).append("\n");
        }

        if (StringUtils.isNotBlank(aceBean.getPrivilegesString())) {
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
                    .append("privileges: " + aceBean.getPrivilegesString()).append("\n");
        }

        if (aceBean.isKeepOrder()) {
            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY))
                    .append("keepOrder: true").append("\n");
        }

        writeRestrictions(aceBean, sb);
        sb.append("\n");
        sb.append("\n");
    }

    private void writeRestrictions(final AceBean aceBean, final StringBuilder sb) {
        final List<Restriction> restrictions = aceBean.getRestrictions();
        if (restrictions.isEmpty()) {
            return;
        }
        sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append("restrictions:");
        sb.append("\n");
        for (Restriction restriction : restrictions) {
            String restrictionsValueString = StringUtils.join(restriction.getValues(), ",");

            String restrictionsValEscaped;
            if (StringUtils.equals(restrictionsValueString, "")) {
                restrictionsValEscaped = "''";
            } else if (restrictionsValueString != null && restrictionsValueString.matches("[A-Za-z0-9,/]+")) {
                restrictionsValEscaped = restrictionsValueString;
            } else {
                restrictionsValEscaped = "'" + restrictionsValueString + "'";
            }

            sb.append(AcHelper.getBlankString(DUMP_INDENTATION_RESTRICTIONS)).append(restriction.getName()).append(": ")
                    .append(restrictionsValEscaped);

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
        String key = structuralDumpElement.getString();
        if (StringUtils.isBlank(key) || !key.matches("[A-Za-z0-9\\-_/.]+")) {
            key = "'" + key + "'";
        }
        sb.append(AcHelper.getBlankString(structuralDumpElement.getLevel() * 2)
                + YAML_STRUCTURAL_ELEMENT_PREFIX + key + MapKey.YAML_MAP_KEY_SUFFIX);

        String comment = structuralDumpElement.getComment();
        if (StringUtils.isNotBlank(comment)) {
            sb.append(" # " + comment);
        }

        sb.append("\n");
        sb.append("\n");
    }

    public int getMapOrder() {
        return mapOrder;
    }

}
