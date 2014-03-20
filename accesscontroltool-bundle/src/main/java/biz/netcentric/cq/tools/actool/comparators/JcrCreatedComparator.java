package biz.netcentric.cq.tools.actool.comparators;

import java.util.Calendar;
import java.util.Comparator;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcrCreatedComparator implements Comparator<Node>{

	private static final String PROPERTY_JCR_CREATED = "jcr:created";
	final Logger LOG = LoggerFactory.getLogger(JcrCreatedComparator.class);

	@Override
	public int compare(final Node node1, final Node node2) {

		try {
			Calendar node1Date = node1.getProperty(PROPERTY_JCR_CREATED).getDate();
			Calendar node2Date = node2.getProperty(PROPERTY_JCR_CREATED).getDate();
			if(node1Date.getTimeInMillis() > node2Date.getTimeInMillis()){
				return -1;
			}else if(node1Date.getTimeInMillis() < node2Date.getTimeInMillis()){
				return 1;
			}
		} catch (ValueFormatException e) {
			LOG.error("Exception: {}",e);
		} catch (PathNotFoundException e) {
			LOG.error("Exception: {}",e);
		} catch (RepositoryException e) {
			LOG.error("Exception: {}",e);
		}
		return 0;
	}
}
