package biz.netcentric.cq.tools.actool.installhook;

import org.osgi.framework.ServiceReference;

import biz.netcentric.cq.tools.actool.aceservice.AceService;

import com.day.jcr.vault.packaging.InstallContext;
import com.day.jcr.vault.packaging.PackageException;

public class AcToolInstallHook extends OsgiAwareInstallHook {

	@Override
	public void execute(InstallContext context) throws PackageException {
		switch (context.getPhase()) {
		case PREPARE:
			 
			// check if AcTool is installed
			log("Installing ACLs through AcToolInstallHook...", context.getOptions());
			ServiceReference aceServiceReference = getServiceReference("biz.netcentric.cq.tools.actool.aceservice.AceService");
			if (aceServiceReference == null) {
				throw new PackageException(
						"Could not get AceService from OSGI service registry. Make sure the ACTool is installed!");
			}
			AceService aceService = (biz.netcentric.cq.tools.actool.aceservice.AceService) getBundleContext()
					.getService(aceServiceReference);
			if (aceService == null) {
				throw new PackageException(
						"Could not instanciate AceService. Make sure the ACTool is installed and check the log for errors");
			}
			try {
				// TODO: refactor the AceService to be able to process arbitrary YAML files
				aceService.execute();
				log("Installed ACLs through AcToolInstallHook!", context.getOptions());
			} finally {
				getBundleContext().ungetService(aceServiceReference);
			}
			break;
		case INSTALLED:
			// apply YAML files from package
			
			break;
		default:	
			// nothing to do in all other phases
			break;

		}
	}
}
