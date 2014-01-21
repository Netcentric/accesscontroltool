package biz.netcentric.cq.tools.actool.aceservice;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Session;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface AceService {
	
	/**
	 * returns a dump of a ACEs set in the system and writes it in an output stream
	 * @param out PrintWriter
	 * @param session session having sufficient rights
	 * @param mapOrder 1:group based, 2: path baswd
	 * @param aceOrder order of the ACEs. 1: deny before allows, 2: no sorting
	 */
	public void returnAceDump(final PrintWriter out, final Session session, final int mapOrder, final int aceOrder);
	
	public void returnAceDumpAsFile(final SlingHttpServletRequest request, final SlingHttpServletResponse response, final Session session, final int mapOrder, final int aceOrder);
	
	public void returnCompleteDumpAsFile(final SlingHttpServletResponse response, Map<String, Set<AceBean>> aceMap,  Set<AuthorizableConfigBean> authorizableSet, final Session session, final int mapOrder, final int aceOrder) throws IOException;
	
	public void purge(final PrintWriter out, final ResourceResolver resourceResolver, final String purgePaths[]);
	
	public String getCompletePathBasedDumpsAsString();
	
	public String getCompletePrincipalBasedDumpsAsString();
	
	public AcInstallationHistoryPojo execute();
	
	public boolean isReadyToStart();
	
	public String purgeACL(final String path);
	
	public String purgeACLs(final String path);
	
	public String purgeAuthorizable(String authorizableId);
	
	public boolean isExecuting();
	
	public String checkPrincipalPermissions(final String principalID, final String path);
	
	public String[] getExcludePaths();
	
	public String getConfigurationRootPath();
	
	public Set<String> getFoundConfigPaths();
	
	Map<String,String> getNewestConfigurationNodes(final String path, final Session session, AcInstallationHistoryPojo status) throws Exception;
	

	

	void installConfigurationFromRequest(SlingHttpServletRequest request,
			PrintWriter out, Session session, boolean dryRun,
			AcInstallationHistoryPojo status,
			Set<AuthorizableInstallationHistory> authorizableHistorySet)
			throws Exception;



	void installConfigurationFromString(List mergedConfigurations,
			AcInstallationHistoryPojo status, Session session,
			Set<AuthorizableInstallationHistory> authorizableHistorySet,
			Map<String, Set<AceBean>> repoDump) throws Exception;

	
	

	
	
}
