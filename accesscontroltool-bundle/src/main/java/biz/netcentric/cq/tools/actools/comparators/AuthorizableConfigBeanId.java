package biz.netcentric.cq.tools.actools.comparators;

import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;

public class AuthorizableConfigBeanId implements Comparator<AuthorizableConfigBean>{
	
	final Logger LOG = LoggerFactory.getLogger(AuthorizableConfigBeanId.class);

	@Override
	public int compare(AuthorizableConfigBean group1, AuthorizableConfigBean group2) {
		return (group1.getPrincipalID().compareTo(group2.getPrincipalID()));
	}

}
