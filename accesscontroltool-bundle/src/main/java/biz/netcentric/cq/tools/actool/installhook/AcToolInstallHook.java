package biz.netcentric.cq.tools.actool.installhook;

import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.installationhistory.HistoryEntry;

import com.day.jcr.vault.packaging.InstallContext;
import com.day.jcr.vault.packaging.PackageException;

public class AcToolInstallHook extends OsgiAwareInstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolInstallHook.class);
    
    private boolean alreadyRan = false;
    
	@Override
	public void execute(InstallContext context) throws PackageException {
	    LOG.debug("Executing install hook for phase {}.", context.getPhase());
	    
		switch (context.getPhase()) {
		case PREPARE:
		    
			/* Workaround for a bug in CQ whereby the package installer goes into a loop if a PackageException is thrown
			 * Daycare Ticket: https://daycare.day.com/home/netcentric/netcentric_de/partner_services/67812.html
			 * Granite Bug: GRANITE-7945, fixed in com.day.jcr.vault, version 2.5.8 (AEM 6.1) 
			 */
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
				AcInstallationHistoryPojo history;
				try {
					history = acService.installYamlFilesFromPackage(context
							.getPackage().getArchive(), context.getSession());

				} catch (Exception e) {
					log("Exception while installing configurations: " + e,
							context.getOptions());
					throw new PackageException(e.getMessage(), e);
				}
				
				if (!history.isSuccess()) {
					for (HistoryEntry entry : history.getException()) {
						log(entry.toString(), context.getOptions());
					}
					throw new PackageException(
							"Could not install configurations. Check log for detailed error message!");
				} else {
					// convert to correct (HTML) linebreaks for the package manager
					String log = history.toString().replaceAll("\\\n", "<br />");
					log(log, context.getOptions());
					log("Installed ACLs successfully through AcToolInstallHook!",
							context.getOptions());
				}
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
