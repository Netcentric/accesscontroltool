/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public class ContentHelper {
    public static final Logger LOG = LoggerFactory.getLogger(ContentHelper.class);

    private ContentHelper() {
    }

    public static boolean createInitialContent(final Session session, final AcInstallationHistoryPojo history, String path,
            Set<AceBean> aceBeanSetFromConfig) throws RepositoryException, PathNotFoundException, ItemExistsException,
            ConstraintViolationException, VersionException, InvalidSerializedDataException, LockException, AccessDeniedException {

        String initialContent = findInitialContentInConfigsForPath(aceBeanSetFromConfig, history);
        if (StringUtils.isBlank(initialContent)) {
            return false;
        } else {

            try {
                importContent(session, path, initialContent, false);
                history.addMessage("Created initial content for path " + path);
                return true;
            } catch (Exception e) {
                history.addWarning("Failed creating initial content for path: " + e);
                LOG.warn("Failed creating initial content for path: " + path + " e=" + e, e);
                return false;
            }

        }
    }

    private static String findInitialContentInConfigsForPath(Set<AceBean> aceBeanSetFromConfig, AcInstallationHistoryPojo history) {
        String initialContent = null;
        for (AceBean aceBean : aceBeanSetFromConfig) {
            String currentInitialContent = aceBean.getInitialContent();
            if (StringUtils.isNotBlank(currentInitialContent)) {
                if (initialContent == null) {
                    initialContent = currentInitialContent;
                } else {
                    // this should not happen as it is validated at YamlConfigurationsValidator#validateInitialContentForNoDuplic already
                    throw new IllegalStateException("Invalid Configuration: Path " + aceBean.getJcrPath()
                            + " defines initial content at two locations");
                }
            }
        }
        return initialContent;
    }

    public static void importContent(final Session session, String path,
            String initialContent, boolean removeNodeIfExists) throws RepositoryException {
        String parentPath = StringUtils.substringBeforeLast(path, "/");
        try {
            session.getNode(parentPath);
        } catch (PathNotFoundException e) {
            throw new PathNotFoundException("Parent path " + parentPath + " for creating content at " + path + " does not exist", e);
        }

        if (removeNodeIfExists) {
            try {
                Node node = session.getNode(path);
                node.remove();
            } catch (PathNotFoundException e) {
                LOG.trace("Path {} does not exist and does not have to be removed therefore", path);
            }
        }

        String rootElementStr = "<jcr:root ";
        if (!initialContent.contains(rootElementStr)) {
            throw new IllegalStateException("Invalid initial content for path " + path + ": " + rootElementStr
                    + " must be provided as root element in XML");
        }
        String initialContentAdjusted = initialContent;
        if (!initialContentAdjusted.contains("xmlns:cq")) {
            initialContentAdjusted = initialContentAdjusted.replace(rootElementStr, rootElementStr
                    + " xmlns:cq=\"http://www.day.com/jcr/cq/1.0\" ");
        }
        if (!initialContentAdjusted.contains("xmlns:jcr")) {
            initialContentAdjusted = initialContentAdjusted.replace(rootElementStr, rootElementStr
                    + " xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" ");
        }
        if (!initialContentAdjusted.contains("xmlns:sling")) {
            initialContentAdjusted = initialContentAdjusted.replace(rootElementStr, rootElementStr
                    + " xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\" ");
        }

        String nodeName = StringUtils.substringAfterLast(path, "/");
        initialContentAdjusted = initialContentAdjusted.replace("jcr:root", nodeName);

        LOG.debug("Adding initial content for path {}\n{}", path, initialContentAdjusted);
        try {
            session.importXML(parentPath, new ByteArrayInputStream(initialContentAdjusted.getBytes()),
                    ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
        } catch (IOException e) {
            throw new RepositoryException("I/O Error during import operation", e);
        }

        LOG.debug("Imported content for path {}\n{}", path, initialContentAdjusted);

    }

}
