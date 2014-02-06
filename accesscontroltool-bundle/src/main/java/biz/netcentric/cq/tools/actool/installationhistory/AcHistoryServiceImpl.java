package biz.netcentric.cq.tools.actool.installationhistory;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrUtil;



@Service
@Component(
		metatype = true,
		label = "AcHistory Service",
		immediate = true,
		description = "Service that writes & fetches Ac installation histories")

@Properties({
	@Property(label = "ACL number of histories to save", name = "AceService.nrOfSavedHistories", value = "5")

})



public class AcHistoryServiceImpl implements AcHistoryService{
	private static final String INSTALLED_CONFIGS_NODE_NAME = "installedConfigs";
	private static final int NR_OF_HISTORIES_TO_SAVE_DEFAULT = 5;
	private static final Logger LOG = LoggerFactory.getLogger(AcHistoryServiceImpl.class);
	private int nrOfSavedHistories;

	@Reference
	private SlingRepository repository;



	@Activate
	public void activate(@SuppressWarnings("rawtypes") final Map properties) throws Exception {
		this.nrOfSavedHistories = PropertiesUtil.toInteger(properties.get("AceService.nrOfSavedHistories"), NR_OF_HISTORIES_TO_SAVE_DEFAULT);

	}

	@Override
	public void persistHistory(AcInstallationHistoryPojo history, final String configurationRootPath){
		Session session = null;
		try {
			try {
				session = repository.loginAdministrative(null);
				Node historyNode = HistoryUtils.persistHistory(session, history, this.nrOfSavedHistories);
				session.save();
				if(history.isSuccess()){
					Node configurationRootNode = session.getNode(configurationRootPath);
					if(configurationRootNode != null){
						persistInstalledConfigurations(historyNode, configurationRootNode, history);
						session.save();
					}
					else{
						String message = "Couldn't find configuration root Node under path: " + configurationRootPath;
						LOG.error(message);
						history.addWarning(message);
					}
				}
			} catch (RepositoryException e) {
				LOG.error("RepositoryException: ", e);
			}
		}finally{
			if(session != null){
				session.logout();
			}
		}
	}

	@Override
	public String[] getInstallationLogPaths()  { 
		Session session = null;
		try {
			session = repository.loginAdministrative(null);
			return HistoryUtils.getHistoryInfos(session);
		} catch (RepositoryException e) {
			LOG.error("RepositoryException: ", e);
		}finally{
			if(session != null){
				session.logout();
			}
		}
		return null;
	}

	@Override
	public String getLogHtml(Session session, String path) {
		return HistoryUtils.getLogHtml(session, path); 
	}
	
	@Override
	public String getLogTxt(Session session, String path) {
		return HistoryUtils.getLogTxt(session, path); 
	}

	@Override
	public String getLastInstallationHistory() {
		Session session = null;
		String history = "";
		try {
			session = repository.loginAdministrative(null);

			Node statisticsRootNode = HistoryUtils.getAcHistoryRootNode(session);
			NodeIterator it = statisticsRootNode.getNodes();

			if(it.hasNext()){
				Node lastHistoryNode = it.nextNode();

				if(lastHistoryNode != null){
					history = getLogHtml(session, lastHistoryNode.getName());
				}
			}else{
				history = "no history found!";
			}
		} catch (RepositoryException e) {
			LOG.error("RepositoryException: ", e);
		}finally{
			if(session != null){
				session.logout();
			}
		}
		return history;
	}
	
	

	public void persistInstalledConfigurations(final Node historyNode, final Node configurationRootNode, AcInstallationHistoryPojo history) {

		try {
			JcrUtil.copy(configurationRootNode, historyNode, INSTALLED_CONFIGS_NODE_NAME);
		} catch (RepositoryException e) {
			String message = e.toString();
			history.setException(e.toString());
			LOG.error("Exception: ", e);
		}

		try {
			history.addMessage("saved installed configuration files under : " + historyNode.getPath() + "/" + INSTALLED_CONFIGS_NODE_NAME);
		} catch (RepositoryException e) {
			LOG.error("Exception: ", e);
		}

	}

	public String showHistory(int n){
		Session session = null;
		String history = "";
		try {
			session = repository.loginAdministrative(null);

			Node statisticsRootNode = HistoryUtils.getAcHistoryRootNode(session);
			NodeIterator it = statisticsRootNode.getNodes();
			int cnt = 1;
			
		while(it.hasNext()){
				Node historyNode = it.nextNode();

				if(historyNode != null && cnt == n){
					history = getLogTxt(session, historyNode.getName());
				}
				cnt++;
			}
		} catch (RepositoryException e) {
			LOG.error("RepositoryException: ", e);
		}finally{
			if(session != null){
				session.logout();
			}
		}
		return history;
	}

}
