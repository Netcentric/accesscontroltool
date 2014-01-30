package biz.netcentric.cq.tools.actool.aceservicejmx;


import java.util.Set;

import org.apache.commons.lang.time.StopWatch;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.installationhistory.AcHistoryService;

@Service
@Component(immediate = true, metatype = true)
@Properties({
	@Property(name = "jmx.objectname", value = "biz.netcentric.cq.tools.actool:id='ac installation'"),
	@Property(name = "pattern", value = "/.*")
})

public class AceServiceMBeanImpl implements AceServiceMBean{  
	
	private static final Logger LOG = LoggerFactory.getLogger(AceServiceMBeanImpl.class);

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
	public String getConfigurationFileLinks() {
		final Set<String> paths = aceService.getFoundConfigPaths();
		StringBuilder sb = new StringBuilder();
		for(String path : paths){
			sb.append("<a href = '/crx/de/index.jsp#/crx.default/jcr%3aroot" + path + "' target=\"_blank'\" >" + path + "</a><br />");
		}
		return  sb.toString();
	} 

	@Override
	public String getSavedLogs()  {
		return acHistoryService.getInstallationLogPaths();
	}

	@Override
	public String pathBasedDump() {
		StopWatch sw = new StopWatch();
		sw.start();
		String dump =  aceService.getCompletePathBasedDumpsAsString();
		sw.stop();
		LOG.info("path based dump took: " + sw.getTime() + " ms");
		return dump;
	}

	@Override
	public String groupBasedDump() {
		return aceService.getCompletePrincipalBasedDumpsAsString();
	}

}
