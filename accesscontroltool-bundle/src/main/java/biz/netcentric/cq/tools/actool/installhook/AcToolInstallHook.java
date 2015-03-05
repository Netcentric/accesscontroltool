package biz.netcentric.cq.tools.actool.installhook;

import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.jcr.vault.packaging.InstallContext;
import com.day.jcr.vault.packaging.PackageException;

public class AcToolInstallHook extends OsgiAwareInstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolInstallHook.class);
    
    // Workaround for a bug in CQ whereby the package installer goes into a loop if a PackageException is thrown
    private boolean alreadyRan = false;
    
	@Override
	public void execute(InstallContext context) throws PackageException {
	    LOG.debug("Executing install hook for phase {}.", context.getPhase());
	    
		switch (context.getPhase()) {
		case PREPARE:
		    
		    if (alreadyRan) {
		        log("Evading attempt to run the install hook twice due to a bug in CQ.", context.getOptions());
		        return;
		    }
		    alreadyRan = true;
			 
			// check if AcTool is installed
			log("Installing ACLs through AcToolInstallHook...", context.getOptions());
			ServiceReference acToolInstallHookService = getServiceReference("biz.netcentric.cq.tools.actool.installhook.AcToolInstallHookService");
			if (acToolInstallHookService == null) {
				throw new PackageException(
						"Could not get AceService from OSGI service registry. Make sure the ACTool is installed!");
			}
			AcToolInstallHookService acService = (AcToolInstallHookService) getBundleContext()
					.getService(acToolInstallHookService);
			if (acService == null) {
				throw new PackageException(
						"Could not instanciate AceService. Make sure the ACTool is installed and check the log for errors");
			}
			//
			try {
				acService.installYamlFilesFromPackage(context.getPackage().getArchive(), context.getSession());
				log("Installed ACLs through AcToolInstallHook!", context.getOptions());
			} catch (Exception e) {
			    log("Exception while installing configurations: " + e, context.getOptions());
			    throw new PackageException(e.getMessage(), e);
			} finally {
				getBundleContext().ungetService(acToolInstallHookService);
			}
			break;
			
		default:	
			// nothing to do in all other phases
			break;

		}
	}
	
	
}
