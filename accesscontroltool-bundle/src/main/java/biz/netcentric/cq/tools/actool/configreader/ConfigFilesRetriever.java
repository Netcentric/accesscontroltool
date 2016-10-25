package biz.netcentric.cq.tools.actool.configreader;

import java.util.Map;

import javax.jcr.Node;

import org.apache.jackrabbit.vault.fs.io.Archive;

/** Retrieves the contents of a AC tool yaml config file from either a package directly (used by install hook) or from the JCR node structure
 * (used by JMX). Both methods return a map with filename-&gt;yaml-config-content entries.
 *
 * @author ghenzler */
public interface ConfigFilesRetriever {

    /** Returns yaml configurations using a given root node. This will only return configuration entries which apply to the current run mode.
     *
     * @param rootNode the root node in the JCR to start looking for yaml-files
     * @return map of yaml configurations by their path location
     * @throws Exception if things go wrong */
    Map<String, String> getConfigFileContentFromNode(Node rootNode) throws Exception;

    /** Returns yaml configurations from a package.  This will only return configuration entries which apply to the current run mode
     *
     * @param archive the Vault Package
     * @return map of yaml configurations by their path location
     * @throws Exception if things go wrong */
    Map<String, String> getConfigFileContentFromPackage(Archive archive) throws Exception;

}
