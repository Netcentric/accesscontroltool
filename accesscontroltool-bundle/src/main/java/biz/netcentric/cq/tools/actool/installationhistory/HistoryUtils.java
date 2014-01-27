package biz.netcentric.cq.tools.actool.installationhistory;


import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actools.comparators.TimestampPropertyComparator;

public class HistoryUtils {

	private static final String PROPERTY_SLING_RESOURCE_TYPE = "sling:resourceType";
	private static final String ACHISTORY_ROOT_NODE = "achistory";
	private static final String STATISTICS_ROOT_NODE = "var/statistics";

	private static final String PROPERTY_TIMESTAMP = "timestamp";
	private static final String PROPERTY_MESSAGES = "messages";
	private static final String PROPERTY_EXECUTION_TIME = "executionTime";
	private static final String PROPERTY_SUCCESS = "success";
	private static final String PROPERTY_INSTALLATION_DATE = "installationDate";
	
	private static final Logger LOG = LoggerFactory.getLogger(HistoryUtils.class);


	public static Node getAcHistoryRootNode(final Session session) throws RepositoryException{
		final Node rootNode = session.getRootNode();
		Node statisticsRootNode = safeGetNode(rootNode, STATISTICS_ROOT_NODE, "nt:unstructured");
		Node acHistoryRootNode = safeGetNode(statisticsRootNode, ACHISTORY_ROOT_NODE, "sling:OrderedFolder");
		return acHistoryRootNode;
	}
	
	public static Node persistHistory(final Session session, AcInstallationHistoryPojo history,  final int nrOfHistoriesToSave) throws RepositoryException{

		Node acHistoryRootNode = getAcHistoryRootNode(session);
		Node newHistoryNode = safeGetNode(acHistoryRootNode, "history_" + System.currentTimeMillis(), "nt:unstructured");
		String path = newHistoryNode.getPath();
		setHistoryNodeProperties(newHistoryNode, history);
		deleteObsoleteHistoryNodes(acHistoryRootNode, nrOfHistoriesToSave);

		Node previousHistoryNode = (Node) acHistoryRootNode.getNodes().next();
		if(previousHistoryNode != null){
			acHistoryRootNode.orderBefore(newHistoryNode.getName(), previousHistoryNode.getName());
		}
		
		String message = "saved history in node: " + path;
		history.addMessage(message);
		LOG.info(message);
		return newHistoryNode;
	}

	private static Node safeGetNode(final Node baseNode, final String name, final String typeToCreate)
			throws RepositoryException {
		if (!baseNode.hasNode(name)) {
			LOG.info("create node: {}", name);
			return baseNode.addNode(name, typeToCreate);

		} else {
			return baseNode.getNode(name);
		}
	}

	private static void setHistoryNodeProperties(final Node historyNode, AcInstallationHistoryPojo history) throws ValueFormatException, VersionException, LockException, ConstraintViolationException, RepositoryException{

		historyNode.setProperty(PROPERTY_INSTALLATION_DATE, history.getInstallationDate().toString());
		historyNode.setProperty(PROPERTY_SUCCESS, history.isSuccess());
		historyNode.setProperty(PROPERTY_EXECUTION_TIME, history.getExecutionTime());
		historyNode.setProperty(PROPERTY_MESSAGES, history.getVerboseMessageHistory());
		historyNode.setProperty(PROPERTY_TIMESTAMP, history.getInstallationDate().getTime());
		historyNode.setProperty(PROPERTY_SLING_RESOURCE_TYPE, "/apps/netcentric/actool/components/historyRenderer");
	}

	private static void deleteObsoleteHistoryNodes(final Node acHistoryRootNode, final int nrOfHistoriesToSave) throws RepositoryException{
		NodeIterator childNodeIt = acHistoryRootNode.getNodes();
		Set<Node> historyChildNodes = new TreeSet<Node>(new TimestampPropertyComparator());

		while(childNodeIt.hasNext()){
			historyChildNodes.add(childNodeIt.nextNode());
		}
		int index = 1;
		for(Node node : historyChildNodes){
			if(index > nrOfHistoriesToSave){
				LOG.info("delete obsolete history node: ", node.getPath());
				node.remove();
			}
			index++;
		}
	}

	public static String getInstallationLogLinks(final Session session) throws RepositoryException{
		Node acHistoryRootNode = getAcHistoryRootNode(session);
		return getAssembledHistoryLinks(acHistoryRootNode);
	}

	private static String getAssembledHistoryLinks(Node acHistoryRootNode)
			throws RepositoryException, PathNotFoundException {
		String messages = "";
		for (NodeIterator iterator =  acHistoryRootNode.getNodes(); iterator.hasNext();) {
			Node node = (Node) iterator.next();
			if(node != null){
				String successStatusString = HtmlConstants.FONT_COLOR_NO_SUCCESS_HTML_OPEN + "failed"  + HtmlConstants.FONT_COLOR_SUCCESS_HTML_CLOSE;
				if(node.getProperty(PROPERTY_SUCCESS).getBoolean()){
					successStatusString = HtmlConstants.FONT_COLOR_SUCCESS_HTML_OPEN + "ok" + HtmlConstants.FONT_COLOR_SUCCESS_HTML_CLOSE;
				}
				
				messages = messages + node.getPath() + " " + "(" + successStatusString + ")" + " <a href ='"+ node.getPath()  + ".html' target=\"_blank'\">" + " (show)" + "</a><br />";
			}
		}
		return messages;
	}

	public static String getLogHtml(Session session, String path) {
		
		StringBuilder sb = new StringBuilder();
		try {
			Node acHistoryRootNode = getAcHistoryRootNode(session);
			Node historyNode = acHistoryRootNode.getNode(path);
			
			if(historyNode != null){
				sb.append("Installation triggered: " + historyNode.getProperty(PROPERTY_INSTALLATION_DATE).getString());
				sb.append("<br />" + historyNode.getProperty(PROPERTY_MESSAGES).getString().replace("\n", "<br />"));
				sb.append("<br /><br />" + "Execution time: " + historyNode.getProperty(PROPERTY_EXECUTION_TIME).getLong() + " ms");
				sb.append("<br />" + "Success: " + historyNode.getProperty(PROPERTY_SUCCESS).getBoolean());
			}
		} catch (RepositoryException e) {
			LOG.error("RepositoryException: {}", e);
		}
		return sb.toString();
	}
}
