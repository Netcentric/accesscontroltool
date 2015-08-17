package biz.netcentric.cq.tools.actool.configReader;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.jcr.vault.fs.io.Archive;
import com.day.jcr.vault.fs.io.Archive.Entry;

@Service
@Component(label = "AC Config Files Retriever", description = "Provides a map path->yamlConfigContent of relevant configs")
public class ConfigFilesRetrieverImpl implements ConfigFilesRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigFilesRetrieverImpl.class);

    private static final String PROP_JCR_DATA = "jcr:data";

    @Reference
    private SlingSettingsService slingSettingsService;

    @Override
    public Map<String, String> getConfigFileContentByFilenameMap(Session session, String jcrRootPath) throws Exception {

        if (StringUtils.isBlank(jcrRootPath)) {
            throw new IllegalArgumentException("No configuration path configured! please check the configuration of AcService!");
        }
        Node node = null;
        try {
            node = session.getNode(jcrRootPath);

        } catch (RepositoryException e) {
            throw new IllegalArgumentException("Configured AC Tool root configuration path (=" + jcrRootPath
                    + ") could not be found or accessed!");
        }

        Map<String, String> configurations = getConfigurations(new NodeInJcr(node));

        return configurations;
    }

    @Override
    public Map<String, String> getConfigFileContentByFilenameMap(Archive archive) throws Exception {
        Entry rootEntry = archive.getJcrRoot();
        if (rootEntry == null) {
            throw new IllegalStateException("Invalid package: It does not contain a JCR root element");
        }
        getConfigurations(new EntryInPackage(archive, "/", rootEntry));
        return null;
    }

    private Map<String, String> getConfigurations(PackageEntryOrNode configFileOrDir) throws Exception {
        Map<String, String> configs = new HashMap<String, String>();

        Set<String> currentRunModes = slingSettingsService.getRunModes();

        for (PackageEntryOrNode entry : configFileOrDir.getChildren()) {
            if (entry.isDirectory()) {
                Map<String, String> resultsFromDir = getConfigurations(entry);
                configs.putAll(resultsFromDir);
                continue;
            }

            if (isRelevantConfiguration(entry.getName(), configFileOrDir.getName(), currentRunModes)) {
                LOG.info("Reading YAML file {}", entry.getName());
                configs.put(entry.getPath(), entry.getContentAsString());
            }

        }
        return configs;
    }

    static boolean isRelevantConfiguration(final String entryName, final String parentName, final Set<String> currentRunModes) {
        if (entryName.endsWith(".yaml")) {
            // extract runmode from parent name (if parent has "." in it)
            Set<String> requiredRunModes = extractRunModesFromName(parentName);
            if (requiredRunModes.isEmpty()) {
                LOG.debug(
                        "Install file '{}', because parent name '{}' does not have a run mode specified.",
                        entryName, parentName);
                return true;
            }

            // check if parent name has the right name
            for (String requiredRunMode : requiredRunModes) {
                if (!currentRunModes.contains(requiredRunMode)) {
                    LOG.debug(
                            "Do not install file '{}', because required run mode '{}' is not set.",
                            entryName, requiredRunMode);
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    static Set<String> extractRunModesFromName(final String name) {
        Set<String> requiredRunModes = new HashSet<String>();

        // extract runmodes from name (separated by ".")
        int positionDot = name.indexOf(".");

        while (positionDot != -1) {
            // find next dot
            int positionPreviousDot = positionDot;
            if ((positionPreviousDot + 1) >= name.length()) {
                // ignore dots at the end!
                return requiredRunModes;
            }
            positionDot = name.indexOf(".", positionPreviousDot + 1);
            final String runMode;
            if (positionDot == -1) {
                runMode = name.substring(positionPreviousDot + 1);
            } else {
                runMode = name.substring(positionPreviousDot + 1, positionDot);
            }
            if (!runMode.isEmpty()) {
                requiredRunModes.add(runMode);
            }
        }
        return requiredRunModes;
    }

    /** Internal representation of either a package entry or a node to be able to use one algorithm for both InstallHook/JMX scenarios. */
    private static interface PackageEntryOrNode {
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
            return node.getPrimaryNodeType().getName().toLowerCase().endsWith("folder");
        }

        @Override
        public String getContentAsString() throws Exception {
            InputStream configInputStream = null;
            try {
                StringWriter writer = new StringWriter();
                configInputStream = JcrUtils.readFile(node);
                IOUtils.copy(configInputStream, writer, "UTF-8");
                String configData = writer.toString();
                if (StringUtils.isNotBlank(configData)) {
                    LOG.info("found configuration data of node: {}", node.getPath());
                    return configData;
                } else {
                    throw new IllegalStateException("File " + node.getPath() + " is empty!");
                }

            } finally {
                if (configInputStream != null) {
                    configInputStream.close();
                }
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
        @SuppressWarnings("unchecked")
        public List<PackageEntryOrNode> getChildren() throws Exception {

            List<PackageEntryOrNode> children = new LinkedList<PackageEntryOrNode>();
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
            LOG.info("Reading YAML file {}", getPath());
            InputStream input = archive.getInputSource(entry).getByteStream();
            if (input == null) {
                throw new IllegalStateException("Could not get input stream from entry " + getPath());
            }
            StringWriter writer = new StringWriter();
            IOUtils.copy(input, writer, "UTF-8");
            return writer.toString();
        }
    }

}
