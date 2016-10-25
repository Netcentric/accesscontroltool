package biz.netcentric.cq.tools.actool.configreader;

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
import javax.jcr.nodetype.NodeType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Component(label = "AC Config Files Retriever", description = "Provides a map path->yamlConfigContent of relevant configs")
public class ConfigFilesRetrieverImpl implements ConfigFilesRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigFilesRetrieverImpl.class);

    @Reference
    private SlingSettingsService slingSettingsService;

    @Override
    public Map<String, String> getConfigFileContentFromNode(Node rootNode) throws Exception {
        if (rootNode == null) {
            throw new IllegalArgumentException("No configuration path configured! please check the configuration of AcService!");
        }
        Map<String, String> configurations = getConfigurations(new NodeInJcr(rootNode));
        return configurations;
    }

    @Override
    public Map<String, String> getConfigFileContentFromPackage(Archive archive) throws Exception {
        Entry rootEntry = archive.getJcrRoot();
        if (rootEntry == null) {
            throw new IllegalStateException("Invalid package: It does not contain a JCR root element");
        }
        Map<String, String> configurations = getConfigurations(new EntryInPackage(archive, "/", rootEntry));
        return configurations;
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
                LOG.debug("Found relevant YAML file {}", entry.getName());
                configs.put(entry.getPath(), entry.getContentAsString());
            }

        }
        return configs;
    }

    static boolean isRelevantConfiguration(final String entryName, final String parentName, final Set<String> currentRunModes) {
        if (!entryName.endsWith(".yaml") && !entryName.equals("config") /* name 'config' without .yaml allowed for backwards compatibility */) {
            return false;
        }

        // extract runmode from parent name (if parent has "." in it)
        Set<Set<String>> requiredRunModes = extractRunModesFromName(parentName);
        if (requiredRunModes.isEmpty()) {
            LOG.debug("Install file '{}', because parent name '{}' does not have a run mode specified.",
                    entryName, parentName);
            return true;
        }

        // check the OR concatenated run modes
        for (Set<String> andRunModes : requiredRunModes) {
        	// within each OR section is a number of AND concatenated run modes
        	boolean restrictionFulfilled = true;
        	for (String andRunMode : andRunModes) {
        		// all must be fulfilled
        		if (!currentRunModes.contains(andRunMode)) {
        			restrictionFulfilled = false;
        			break;
        		}
        	}
        	if (restrictionFulfilled) {
        		LOG.debug("The following run modes are all set: {}, there proceed installing file '{}'", StringUtils.join(andRunModes,","), entryName);
        		return true;
        	}
        }
        LOG.debug("The run mode restrictions could not be fullfilled, therefore not installing file '{}'", entryName);
        return false;
    }

    /**
     * 
     * @param name a name containing a number of runmodes concatenated with AND and OR. The name must stick to the following grammar
     * <pre>&lt;somename&gt;['.'{&lt;runmode&gt;'.'|&lt;runmode&gt;','}]</pre>
     * The separator '.' means AND, "," means OR.
     * As usual in most programming languages the AND has a higher precendence.
     * @return the run modes being extracted from the given name 
     * (the outer set of run modes represent OR concatenated run modes, the inner set AND concatenated run modes)
     */
    static Set<Set<String>> extractRunModesFromName(final String name) {
        Set<Set<String>> requiredRunModes = new HashSet<Set<String>>();

        // strip prefix as the name starts usually with config.
        int positionDot = name.indexOf(".");
        if (positionDot == -1) {
        	return requiredRunModes;
        }
        
        String allSegments = name.substring(positionDot + 1);
        String[] orSegments = allSegments.split(",");
        for (String orSegment : orSegments) {
        	Set<String> andRunModes = new HashSet<String>();
        	String[] andSegments = orSegment.split("\\.");
        	for (String andSegment : andSegments) {
        		andRunModes.add(andSegment);
        	}
        	requiredRunModes.add(andRunModes);
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
            return node.getPrimaryNodeType().isNodeType(NodeType.NT_FOLDER);
        }

        @Override
        public String getContentAsString() throws Exception {
            final InputStream configInputStream = JcrUtils.readFile(node);
            try {
                StringWriter writer = new StringWriter();
                IOUtils.copy(configInputStream, writer, "UTF-8");
                String configData = writer.toString();
                if (StringUtils.isNotBlank(configData)) {
                    LOG.debug("found configuration data of node: {}", node.getPath());
                    return configData;
                } else {
                    throw new IllegalStateException("File " + node.getPath() + " is empty!");
                }

            } finally {
                configInputStream.close();
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
            LOG.debug("Reading YAML file {}", getPath());
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
