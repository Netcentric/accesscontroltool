package biz.netcentric.cq.tools.actool.installhook;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Session;

import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

import com.day.jcr.vault.fs.io.Archive;
import com.day.jcr.vault.fs.io.Archive.Entry;
import com.day.jcr.vault.packaging.InstallContext;
import com.day.jcr.vault.packaging.PackageException;

public class AcToolInstallHook extends OsgiAwareInstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolInstallHook.class);
    
	@Override
	public void execute(InstallContext context) throws PackageException {
	    LOG.debug("Executing install hook for phase {}.", context.getPhase());
	    
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
				installConfigs(context.getPackage().getArchive(), context.getSession(), aceService);
				log("Installed ACLs through AcToolInstallHook!", context.getOptions());
			} catch (Exception e) {
			    LOG.error(e.getMessage());
			    throw new PackageException(e.getMessage(), e);
			} finally {
				getBundleContext().ungetService(aceServiceReference);
			}
			break;
			
		default:	
			// nothing to do in all other phases
			break;

		}
	}
	
	private void installConfigs(Archive archive, Session session, AceService aceService) {
        AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
        Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet = new LinkedHashSet<AuthorizableInstallationHistory>();
        
        try {
            Map<String, String> configs = getConfigurations(archive, archive.getJcrRoot(), session);
            LOG.debug("Configurations = {}", configs);
            aceService.installNewConfigurations(session, history, configs, authorizableInstallationHistorySet);
        } catch (AuthorizableCreatorException e) {
            history.setException(e.toString());
            // here no rollback of authorizables necessary since session wasn't
            // saved
        } catch (Exception e) {
            LOG.error("Exception in AceServiceImpl: {}", e);
            history.setException(e.toString());
        } finally {
            // TODO: acHistoryService.persistHistory(history, this.configurationPath);
        }	    
	}

    private Map<String, String> getConfigurations(Archive archive, Entry parent, Session session) throws IOException {
        Map<String, String> configs = new HashMap<String, String>();
        // Read the configuration files from the archive
        for (Entry entry : parent.getChildren()) {
            if (entry.isDirectory()) {
                configs.putAll(getConfigurations(archive, entry, session));
            } else {
                if (entry.getName().endsWith(".yaml")) {
                    LOG.info("Reading YAML file {}", entry.getName());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(archive.getInputSource(entry).getByteStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    configs.put(entry.getName(), sb.toString()); // FIXME: key should be entry path, not name
                }
            }
        }
        return configs;
    }
}
