package biz.netcentric.cq.tools.actool.configReader;

import java.util.Map;

import javax.jcr.Session;

import com.day.jcr.vault.fs.io.Archive;

/** Retrieves the contents of a AC tool yaml config file from either a package directly (used by install hook) or from the JCR node structure
 * (used by JMX). Both methods return a map with filename->yaml-config-content entries.
 *
 * @author ghenzler */
public interface ConfigFilesRetriever {

    /** Returns yaml configurations by their path location using a jcr base path.
     *
     * @param session the jcr session
     * @param jcrRootPath the root path in the JCR to start looking for yaml-files
     * @return map of yaml configurations by their path location
     * @throws Exception if things go wrong */
    Map<String, String> getConfigFileContentByFilenameMap(Session session, String jcrRootPath) throws Exception;

    /** Returns yaml configurations by their path location from a package.
     *
     * @param archive the Vault Package
     * @return map of yaml configurations by their path location
     * @throws Exception if things go wrong */
    Map<String, String> getConfigFileContentByFilenameMap(Archive archive) throws Exception;

}
