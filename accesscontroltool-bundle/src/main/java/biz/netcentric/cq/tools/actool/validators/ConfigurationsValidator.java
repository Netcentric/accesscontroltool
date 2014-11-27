package biz.netcentric.cq.tools.actool.validators;

import java.util.Collection;
import java.util.Set;

public interface ConfigurationsValidator {

    /**
     * Method that checks if a group in the current configuration file was
     * already defined in another configuration file which has been already
     * processed
     * 
     * @param groupsFromAllConfig
     *            set holding all names of groups of all config files which have
     *            already been processed
     * @param groupsFromCurrentConfig
     *            set holding all names of the groups from the current
     *            configuration
     * @param configPath
     *            repository path of current config
     * @throws IllegalArgumentException
     */
    public abstract void validateDoubleGroups(Set<String> groupsFromAllConfig,
            Set<String> groupsFromCurrentConfig, String configPath)
            throws IllegalArgumentException;

    /**
     * Method that checks if both configuration sections (group and ACE) have
     * content
     * 
     * @param configPath
     *            repository path of current config
     * @param yamlList
     *            list holding the group and ACE configuration as LinkedHashMap
     *            (as returned by YAML parser)
     * @throws IllegalArgumentException
     */
    public abstract void validateSectionContentExistence(String configPath,
            Collection<?> configurations) throws IllegalArgumentException;

    /**
     * Method that checks if mandatory configuration section identifiers (group
     * and ACE) exist in the current configuration file
     * 
     * @param entry
     *            Map.Entry containing a configuration
     * @throws IllegalArgumentException
     */
    public abstract void validateMandatorySectionIdentifiersExistence(
            String configuration, String filePath);

    /**
     * Method that checks if only valid configuration section identifiers (group
     * (and optional users) and ACE) exist in the current configuration file
     * 
     * @param sectionIdentifiers
     * @param filePath
     * @throws IllegalArgumentException
     */
    public abstract void validateSectionIdentifiers(
            Set<String> sectionIdentifiers, String filePath)
            throws IllegalArgumentException;

}