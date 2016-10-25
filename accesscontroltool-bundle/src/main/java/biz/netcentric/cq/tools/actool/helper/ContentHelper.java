/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;

import javax.jcr.AccessDeniedException;
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
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.SubArchive;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.Text;
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
                importContent(session, path, initialContent);
                history.addMessage("Created initial content for path " + path);
                return true;
            } catch (Exception e) {
                String msg = "Failed creating initial content for path " + path + ": " + e;
                history.addWarning(msg);
                LOG.warn(msg);
                LOG.debug("Exception: " + e, e); // log stack trace only debug
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

    public static void importContent(final Session session, final String path,
            String contentXmlStr) throws RepositoryException {
        String parentPath = StringUtils.substringBeforeLast(path, "/");
        try {
            session.getNode(parentPath);
        } catch (PathNotFoundException e) {
            throw new PathNotFoundException("Parent path " + parentPath + " for creating content at " + path + " does not exist", e);
        }

        String rootElementStr = "<jcr:root ";
        if (!contentXmlStr.contains(rootElementStr)) {
            throw new IllegalStateException("Invalid initial content for path " + path + ": " + rootElementStr
                    + " must be provided as root element in XML");
        }
        String contentXmlStrAdjusted = contentXmlStr;
        if (!contentXmlStrAdjusted.contains("xmlns:cq")) {
            contentXmlStrAdjusted = contentXmlStrAdjusted.replace(rootElementStr, rootElementStr
                    + " xmlns:cq=\"http://www.day.com/jcr/cq/1.0\" ");
        }
        if (!contentXmlStrAdjusted.contains("xmlns:jcr")) {
            contentXmlStrAdjusted = contentXmlStrAdjusted.replace(rootElementStr, rootElementStr
                    + " xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" ");
        }
        if (!contentXmlStrAdjusted.contains("xmlns:sling")) {
            contentXmlStrAdjusted = contentXmlStrAdjusted.replace(rootElementStr, rootElementStr
                    + " xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\" ");
        }

        LOG.debug("Importing content for path {}\n{}", path, contentXmlStrAdjusted);
        try {

            Archive archive = new SingleContentFileArchive(path, contentXmlStrAdjusted);

            ImportOptions importOptions = new ImportOptions();
            importOptions.setAutoSaveThreshold(Integer.MAX_VALUE); // IMPORTANT: this disables saving the session in Importer
            importOptions.setStrict(true);
            importOptions.setListener(new ProgressTrackerListener() {

                @Override
                public void onMessage(Mode mode, String action, String pathForListener) {
                    LOG.debug("FileVault: " + action + " " + pathForListener);
                }

                @Override
                public void onError(Mode mode, String pathForListener, Exception e) {
                    throw new IllegalArgumentException("Invalid content fragment at path " + pathForListener + ": " + e, e);
                }

            });

            Importer importer = new Importer(importOptions);

            Node importRoot = session.getNode("/");
            // importer uses session from importRoot node
            importer.run(archive, importRoot);

        } catch (IOException e) {
            throw new RepositoryException("I/O Error during import operation: " + e, e);
        } catch (ConfigurationException e) {
            throw new RepositoryException("ConfigurationException during import operation: " + e, e);
        }

        LOG.debug("Imported content for path {}\n{}", path, contentXmlStrAdjusted);

    }

    static class SingleContentFileArchive implements Archive {

        private final String filterPath;
        private final String xmlContent;

        private final Entry root;

        public SingleContentFileArchive(String path, String xmlContent) {
            this.filterPath = path;
            this.xmlContent = xmlContent;
            // the contructor SingleContentFileArchiveEntry automatically creates the tree to the xml file
            this.root = new SingleContentFileArchiveEntry("/" + Constants.ROOT_DIR + path + "/" + Constants.DOT_CONTENT_XML);
        }

        @Override
        public void open(boolean strict) throws IOException {
            // nothing to do on open and close
        }

        @Override
        public void close() {
            // nothing to do on open and close
        }

        @Override
        public InputStream openInputStream(Entry entry) throws IOException {
            if (Constants.DOT_CONTENT_XML.equals(entry.getName())) {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
                return inputStream;
            } else {
                return null;
            }

        }

        @Override
        public VaultInputSource getInputSource(Entry entry) throws IOException {
            if (Constants.DOT_CONTENT_XML.equals(entry.getName())) {
                InputStream is = openInputStream(entry);
                VaultInputSource vaultInputSource = new VaultInputSource(is) {

                    @Override
                    public long getContentLength() {
                        return xmlContent.length();
                    }

                    @Override
                    public long getLastModified() {
                        return 0;
                    }

                };
                return vaultInputSource;
            } else {
                return null;
            }

        }

        @Override
        public Entry getRoot() throws IOException {
            return root;
        }

        public Entry getEntry(String path) throws IOException {
            String[] segs = Text.explode(path, '/');
            Entry root = getRoot();
            for (String name : segs) {
                root = root.getChild(name);
                if (root == null) {
                    break;
                }
            }
            return root;
        }

        public Entry getJcrRoot() throws IOException {
            return getRoot().getChild(Constants.ROOT_DIR);
        }

        public Archive getSubArchive(String rootPath, boolean asJcrRoot) throws IOException {
            Entry root = getEntry(rootPath);
            return root == null ? null : new SubArchive(this, root, asJcrRoot);
        }

        @Override
        public MetaInf getMetaInf() {
            DefaultMetaInf defaultMetaInf = new DefaultMetaInf();
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            PathFilterSet pathFilterSet = new PathFilterSet();
            pathFilterSet.setRoot(filterPath); // the only non-default value
            filter.add(pathFilterSet);
            defaultMetaInf.setFilter(filter);
            Properties props = new Properties();
            defaultMetaInf.setProperties(props);
            VaultSettings settings = new VaultSettings();
            defaultMetaInf.setSettings(settings);
            return defaultMetaInf;
        }

        static class SingleContentFileArchiveEntry implements Entry {

            private Entry child;
            private String name;

            SingleContentFileArchiveEntry(String relPath) {
                String[] pathBits = relPath.split("/", 2);
                name = pathBits[0];
                child = pathBits.length == 2 ? new SingleContentFileArchiveEntry(pathBits[1]) : null;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isDirectory() {
                return child != null;
            }

            @Override
            public Collection<? extends Entry> getChildren() {
                return child != null ? Arrays.asList(child) : null;
            }

            @Override
            public Entry getChild(String name) {
                return (name != null && name.equals(child.getName())) ? child : null;
            }

        }

    }

}
