package biz.netcentric.cq.tools.actool.aceservicejmx;

import javax.jcr.RepositoryException;

import com.adobe.granite.jmx.annotation.Description;
/**
 * exposes functionalities and of the Netcentric AC-Tool
 * @author jochenkoschorke
 *
 */
public interface AceServiceMBean {
	
	boolean isReadyToStart();
	
	@Description("executes the installation of the ACE configuration(s)")
	String execute();
	
	@Description("purges the AccessControlList of the given path, if existing")
	String purgeACL(final String path);
	
	@Description("purges all AccessControlLists under the given path and its subpaths, if existing")
	String purgeACLs(final String path);
	
	@Description("purges an authorizable and all its ACEs from the system")
	String purgeAuthorizable(final String authorizableId);
	
	@Description("provides status and links to the saved history logs")
	String getSavedLogs() throws RepositoryException;
	
	@Description("shows execution status of the ac tool")
	public boolean isExecuting();
	
	@Description("returns a dump of all groups and path based dump all ACLs")
	public String pathBasedDump();
	
	@Description("returns a dump of all groups andfroup based dump all ACEs")
	public String groupBasedDump();
		
	@Description("returns links to the existing configuration files in CRX")
	public String getConfigurationFileLinks();
	
	
	
}
