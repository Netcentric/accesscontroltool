package biz.netcentric.cq.tools.actools.comparators;

import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class NodeCreatedComparator implements Comparator<Node>{

	private static final String PROPERTY_JCR_CREATED = "jcr:created";

	@Override
	public int compare(Node node1, Node node2) {
		
		final Logger LOG = LoggerFactory.getLogger(NodeCreatedComparator.class);
		Date node1Date = null;
        Date node2Date = null;
        
        try {
        	node1Date = getArticleDateProperty(node1);
            node2Date = getArticleDateProperty(node2);

            if (node1Date != null && node2Date != null && node1Date.after(node2Date)) {
                return -1;
            } else if (node1Date != null && node2Date != null && node2Date.after(node1Date)) {
                return 1;
            }

        } catch (RepositoryException e) {
            LOG.error(e.toString());
        }

        return 0;
	}
	 
	/** @param node article node
     * @return Date creation date of the article
     * @throws RepositoryException */
    public static Date getArticleDateProperty(final Node node)
            throws RepositoryException {
        Date date = null;
        Property dateProp;
        if (node.hasProperty(PROPERTY_JCR_CREATED)) {
            dateProp = node.getProperty(PROPERTY_JCR_CREATED);
            Calendar cal = dateProp.getDate();
            if (cal != null) {
                date = cal.getTime();
            }
        }
        return date;
    }
}
