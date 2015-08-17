package biz.netcentric.cq.tools.actool.installhook;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.configReader.ConfigFilesRetriever;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

import com.day.jcr.vault.fs.io.Archive;

@Component
@Service(value = AcToolInstallHookService.class)
public class AcToolInstallHookServiceImpl implements AcToolInstallHookService {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolInstallHookServiceImpl.class);

    @Reference
    private AceService aceService;

    @Reference
    private ConfigFilesRetriever configFilesRetriever;

    @Override
    public void installYamlFilesFromPackage(Archive archive, Session session)
            throws Exception {
        AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
        Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet = new LinkedHashSet<AuthorizableInstallationHistory>();

        try {
            Map<String, String> configs = configFilesRetriever.getConfigFileContentByFilenameMap(archive);
            aceService.installNewConfigurations(session, history, configs, authorizableInstallationHistorySet);
        } catch (Exception e) {
            history.addError(e.toString());
            throw e;
        } finally {
            // TODO: acHistoryService.persistHistory(history,
            // this.configurationPath);
        }
    }
}
