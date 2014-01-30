package biz.netcentric.cq.tools.actool.aceservice;

import java.io.IOException;
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
	
	
	
	public void returnAceDumpAsFile(final SlingHttpServletRequest request, final SlingHttpServletResponse response, final Session session, final int mapOrder, final int aceOrder);
	
	public void returnCompleteDumpAsFile(final SlingHttpServletResponse response, Map<String, Set<AceBean>> aceMap,  Set<AuthorizableConfigBean> authorizableSet, final Session session, final int mapOrder, final int aceOrder) throws IOException;
	
	public void purge(final ResourceResolver resourceResolver, final String purgePaths[]);
	
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
	
	public Map<String,String> getNewestConfigurationNodes(final String path, final Session session, AcInstallationHistoryPojo status) throws Exception;
	
	public void installConfigurationFromString(List mergedConfigurations,
			AcInstallationHistoryPojo status, final Session session,
			Set<AuthorizableInstallationHistory> authorizableHistorySet,
			Map<String, Set<AceBean>> repoDump) throws Exception;

	
	

	
	
}
