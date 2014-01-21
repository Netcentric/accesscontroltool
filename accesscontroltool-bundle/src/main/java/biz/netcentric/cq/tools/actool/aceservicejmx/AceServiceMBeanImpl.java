package biz.netcentric.cq.tools.actool.aceservicejmx;


import java.util.Set;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Property;
import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.installationhistory.AcHistoryService;

@Service
@Component(immediate = true, metatype = true)
@Properties({
	@Property(name = "jmx.objectname", value = "biz.netcentric.cq.tools.actool:id='ac installation'"),
	@Property(name = "pattern", value = "/.*")
})

public class AceServiceMBeanImpl implements AceServiceMBean{  

	@Reference
	AceService aceService;
	
	@Reference
	AcHistoryService acHistoryService;

	@Override
	public String execute() {
		return aceService.execute().toString();
	}

	@Override
	public boolean isReadyToStart() {
		return aceService.isReadyToStart(); 
	}

	@Override
	public String purgeACL(final String path) {
		return aceService.purgeACL(path);
	}

	@Override
	public String purgeACLs(final String path) {
		return aceService.purgeACLs(path);
	}

	@Override
	public String purgeAuthorizable(String authorizableId) {
		return aceService.purgeAuthorizable(authorizableId);
	}

	@Override
	public boolean isExecuting() {
		return aceService.isExecuting();
	}

	@Override
	public String getConfigurationFiles() {
		final Set<String> paths = aceService.getFoundConfigPaths();
		StringBuilder sb = new StringBuilder();
		for(String path : paths){
			sb.append(path + "<br />");
		}
		
		return  sb.toString();
	} 

	@Override
	public String getSavedLogs()  {
		return acHistoryService.getLastInstallationLog();
	}

	

	@Override
	public String pathBasedDump() {
		return aceService.getCompletePathBasedDumpsAsString();
	}

	@Override
	public String groupBasedDump() {
		return aceService.getCompletePrincipalBasedDumpsAsString();
	}
}
