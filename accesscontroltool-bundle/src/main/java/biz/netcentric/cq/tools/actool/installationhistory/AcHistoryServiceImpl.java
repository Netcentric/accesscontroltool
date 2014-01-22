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
	public void persistHistory(AcInstallationHistoryPojo history){
		Session session = null;
		try {
			try {
				session = repository.loginAdministrative(null);
				HistoryUtils.persistHistory(session, history, this.nrOfSavedHistories);
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
	public String getInstallationLogPaths()  {
		Session session = null;
		try {
			session = repository.loginAdministrative(null);
			return HistoryUtils.getInstallationLogLinks(session);
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
	public String getLastInstallationHistory() {
		Session session = null;
		String history = "";
		try {
			
			session = repository.loginAdministrative(null);
			final Node rootNode = session.getRootNode();
			Node statisticsRootNode = HistoryUtils.getAcHistoryRootNode(session);
			NodeIterator it = statisticsRootNode.getNodes();
			Node lastHistoryNode = it.nextNode();
			
			if(lastHistoryNode != null){
				history = getLogHtml(session, lastHistoryNode.getName());
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
