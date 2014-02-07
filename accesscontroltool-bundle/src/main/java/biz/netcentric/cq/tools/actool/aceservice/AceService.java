package biz.netcentric.cq.tools.actool.aceservice;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.jcr.Session;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface AceService {
	
	/**
	 * method that returns a dump comprising  aces as file
	 * @param response
	 * @param aceMap
	 * @param authorizableSet
	 * @param session
	 * @param mapOrder
	 * @param aceOrder
	 * @throws IOException
	 */
	
	public void returnAceDumpAsFile(final SlingHttpServletRequest request, final SlingHttpServletResponse response, final Session session, final int mapOrder, final int aceOrder);
	
	/**
	 * method that returns a dump comprising of all groups and all aces as file
	 * @param response
	 * @param aceMap
	 * @param authorizableSet
	 * @param session
	 * @param mapOrder
	 * @param aceOrder
	 * @throws IOException
	 */
	
	public void returnCompleteDumpAsFile(final SlingHttpServletResponse response, Map<String, Set<AceBean>> aceMap,  Set<AuthorizableConfigBean> authorizableSet, final Session session, final int mapOrder, final int aceOrder) throws IOException;
	
	/**
	 * method that return a dump comprising of all groups and all aces in path based view
	 * @return
	 */
	public String getCompletePathBasedDumpsAsString();
	
	/**
	 * method that return a dump comprising of all groups and all aces in principal based view
	 * @return
	 */
	public String getCompletePrincipalBasedDumpsAsString();
	
	/**
	 * method that starts the execution of the ac installation
	 * @return
	 */
	public AcInstallationHistoryPojo execute();
	
	/**
	 * method that indicates whether the service is ready for installation (if at least one configurations was found in repository)
	 * @return true if ready, otherwise false
	 */
	public boolean isReadyToStart();
	
	/**
	 * purges all acls of the node specified by path (no deletion of acls of subnodes)
	 * @param path 
	 * @return status message
	 */
	public String purgeACL(final String path);
	
	/**
	 * purges all acls of the node specified by path and all acls of all subnodes
	 * @param path
	 * @return status message
	 */
	public String purgeACLs(final String path);
	
	/**
	 * method that purges authorizable(s) and all respective aces from the system
	 * @param authorizableId
	 * @return status message
	 */
	public String purgeAuthorizables(String authorizableIds);
	
	/**
	 * returns current execution status
	 * @return true if the service is executing, false if not
	 */
	public boolean isExecuting();
	
	/**
	 * returns the paths under jcr:root witch are excluded from search for rep:policy nodes in OSGi configuration
	 * @return String array containing the paths
	 */
	public String[] getQueryExcludePaths();
	
	/**
	 * return the path in repository under witch the ac confiuration are stored
	 * @return node path in repository
	 */
	public String getConfigurationRootPath();
	
	/**
	 * return a set containing the paths to the newest configurations under the configuration root path
	 * @return set containing paths
	 */
	public Set<String> getCurrentConfigurationPaths();
	
	/**
	 * returns map holding the node paths to the newest configurations as key and the textual yaml configuation as entries
	 * @param configurationsRootPath paths under which the ac configurations get stored in repository
	 * @param session
	 * @param history history object
	 * @return map holding the newest configurations
	 * @throws Exception
	 */
	public Map<String,String> getNewestConfigurationNodes(final String configurationsRootPath, final Session session, AcInstallationHistoryPojo history) throws Exception;
	
	
	public String purgAuthorizablesFromConfig();
	
	
}
