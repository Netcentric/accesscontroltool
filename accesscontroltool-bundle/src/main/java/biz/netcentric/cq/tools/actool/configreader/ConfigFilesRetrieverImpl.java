package biz.netcentric.cq.tools.actool.configreader;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.slingsettings.ExtendedSlingSettingsService;

@Component()
public class ConfigFilesRetrieverImpl implements ConfigFilesRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigFilesRetrieverImpl.class);

    private static final String PACKAGE_BASE_PATH = "/jcr_root";

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ExtendedSlingSettingsService slingSettingsService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    @Override
    public Map<String, String> getConfigFileContentFromNode(String rootPath, Session session) throws Exception {
        Node rootNode = session.getNode(rootPath);
        if (rootNode == null) {
            throw new IllegalArgumentException("No configuration path configured! please check the configuration of AcService!");
        }
        Map<String, String> configurations = getConfigurations(new NodeInJcr(rootNode), Collections.<String> emptyList());
        return configurations;
    }

    @Override
    public Map<String, String> getConfigFileContentFromPackage(Archive archive, Collection<String> configFilePatterns) throws Exception {
        Entry rootEntry = archive.getJcrRoot();
        if (rootEntry == null) {
            throw new IllegalStateException("Invalid package: It does not contain a JCR root element");
        }
        Map<String, String> configurations = getConfigurations(new EntryInPackage(archive, "", rootEntry), configFilePatterns);
        return configurations;
    }

    private Map<String, String> getConfigurations(PackageEntryOrNode configFileOrDir, Collection<String> configFilePatterns)
            throws Exception {
        Map<String, String> configs = new TreeMap<String, String>();

        for (PackageEntryOrNode entry : configFileOrDir.getChildren()) {
            if (entry.isDirectory()) {
                Map<String, String> resultsFromDir = getConfigurations(entry, configFilePatterns);
                configs.putAll(resultsFromDir);
                continue;
            }
            if (isRelevantConfiguration(entry, configFileOrDir.getName(), slingSettingsService, configFilePatterns)) {
                LOG.debug("Found relevant YAML file {}", entry.getName());
                String pathWithoutJcrRoot = StringUtils.removeStart(entry.getPath(), PACKAGE_BASE_PATH);
                configs.put(pathWithoutJcrRoot, entry.getContentAsString());
            }
        }
        return configs;
    }

    static boolean isRelevantConfiguration(final PackageEntryOrNode entry, final String parentName,
            ExtendedSlingSettingsService slingSettings, Collection<String> configFilePatterns) throws Exception {
        boolean matchPattern = configFilePatterns.isEmpty();
        for (String pathPattern : configFilePatterns) {
            if (entry.getPath().matches(pathPattern)) {
                matchPattern = true;
            }
        }
        if (!matchPattern) {
            LOG.info("Skipped file '{}' because it didn't match any path pattern", entry.getPath());
            return false;
        }
        String entryName = entry.getName();
        // name 'config' without .yaml allowed for backwards compatibility
        if (!entryName.endsWith(".yaml") && !entryName.equals("config")) {
            return false;
        }

        // extract runmode from parent name (if parent has "." in it)
        String runModeSpec = extractRunModeSpecFromName(parentName);
        if (runModeSpec.isEmpty()) {
            LOG.debug("Install file '{}', because parent name '{}' does not have a run mode specified.",
                    entryName, parentName);
            return true;
        }

        // check run mode spec
        final boolean restrictionFulfilled = slingSettings.isMatchingRunModeSpec(runModeSpec);
        if (restrictionFulfilled) {
            LOG.debug("The relevant run modes are all set, therefore proceed installing file '{}'", entryName);
        } else {
            LOG.debug("The run mode restrictions could not be fullfilled, therefore not installing file '{}'", entryName);
        }
        return restrictionFulfilled;
    }

    static String extractRunModeSpecFromName(final String name) {
        // strip prefix as the name starts usually with config.
        int positionDot = name.indexOf(".");
        if (positionDot == -1) {
            return "";
        }

        return name.substring(positionDot + 1);
    }

    /** Internal representation of either a package entry or a node to be able to use one algorithm for both InstallHook/JMX scenarios. */
    static interface PackageEntryOrNode {
        String getName() throws Exception;

        String getPath() throws Exception;

        List<PackageEntryOrNode> getChildren() throws Exception;

        boolean isDirectory() throws Exception;

        String getContentAsString() throws Exception;
    }

    private static class NodeInJcr implements PackageEntryOrNode {

        private final Node node;

        public NodeInJcr(Node node) {
            this.node = node;
        }

        @Override
        public String getName() throws Exception {
            return node.getName();
        }

        @Override
        public String getPath() throws Exception {
            return node.getPath();
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<PackageEntryOrNode> getChildren() throws Exception {
            List<PackageEntryOrNode> children = new LinkedList<PackageEntryOrNode>();
            Iterator<Node> childNodesIt = node.getNodes();
            while (childNodesIt.hasNext()) {
                children.add(new NodeInJcr(childNodesIt.next()));
            }
            return children;
        }

        @Override
        public boolean isDirectory() throws Exception {
            return node.getPrimaryNodeType().isNodeType(NodeType.NT_FOLDER);
        }

        @Override
        public String getContentAsString() throws Exception {
            try (InputStream configInputStream = JcrUtils.readFile(node)) {
                StringWriter writer = new StringWriter();
                IOUtils.copy(configInputStream, writer, "UTF-8");
                String configData = writer.toString();
                LOG.debug("Found configuration data of node: {} with {} chars", node.getPath(), configData.length());
                return configData;
            }
        }
    }

    private static class EntryInPackage implements PackageEntryOrNode {

        private final Entry entry;
        private final String path;
        private final Archive archive;

        public EntryInPackage(Archive archive, String parentPath, Entry entry) {
            this.archive = archive;
            this.entry = entry;
            path = parentPath + "/" + entry.getName();
        }

        @Override
        public String getName() throws Exception {
            return entry.getName();
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public List<PackageEntryOrNode> getChildren() throws Exception {

            List<PackageEntryOrNode> children = new LinkedList<>();
            Collection<? extends Entry> entryChildren = entry.getChildren();
            for (Entry childEntry : entryChildren) {
                children.add(new EntryInPackage(archive, getPath(), childEntry));
            }
            return children;
        }

        @Override
        public boolean isDirectory() throws Exception {
            return entry.isDirectory();
        }

        @Override
        public String getContentAsString() throws Exception {
            LOG.debug("Reading YAML file {}", getPath());
            try (InputStream input = archive.getInputSource(entry).getByteStream()) {
                if (input == null) {
                    throw new IllegalStateException("Could not get input stream from entry " + getPath());
                }
                StringWriter writer = new StringWriter();
                IOUtils.copy(input, writer, "UTF-8");
                return writer.toString();
            }
        }
    }

}
