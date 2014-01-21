package biz.netcentric.cq.tools.actool.helper;

import java.security.Principal;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.Privilege;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;

/**
 * class that wraps an AccessControlEntry and stores an additional path information
 * Also provides some getter methods which return ACE data. Created and used during the reading of ACEs from a system, in order to create a ACE Dump
 * @author jochenkoschorke
 *
 */
public class AceWrapper {
	private static final Logger LOG = LoggerFactory.getLogger(AceWrapper.class);
	private static final String PERM_DENY = "deny";
	private static final String PERM_ALLOW = "allow";
	private static final String REP_GLOB_RESTRICTION = "rep:glob";
	private AccessControlEntry ace;
	private String jcrPath;
	
	
	public AceWrapper(AccessControlEntry ace, String jcrPath) {
		super();
		this.ace = ace;
		this.jcrPath = jcrPath;
	}

	public AccessControlEntry getAce() {
		return ace;
	}

	public void setAce(AccessControlEntry ace) {
		this.ace = ace;
	}

	public String getJcrPath() {
		return jcrPath;
	}
    public Principal getPrincipal(){
    	return this.ace.getPrincipal();
    }
	public void setJcrPath(String jcrPath) {
		this.jcrPath = jcrPath;
	}
	@Override
	public boolean equals(Object obj) {
		return this.toString().equals(obj.toString());
	}
	@Override
	public String toString() {
		return this.ace.toString();
	}
	public Privilege[] getPrivileges(){
		return ace.getPrivileges();
	}
	public boolean isAllow(){
		return ((JackrabbitAccessControlEntry)this.ace).isAllow();
	}
	public Value getRestrictionAsValue(String name) throws RepositoryException{
		return ((JackrabbitAccessControlEntry)ace).getRestriction(name);
	}
	public String getRestrictionAsString(String name) throws RepositoryException{
		Value val = ((JackrabbitAccessControlEntry)ace).getRestriction(name);
		if(val != null){
		   return ((JackrabbitAccessControlEntry)ace).getRestriction(name).getString();
		}
		return "";
	}
	public String getPrivilegesString(){
		Privilege[] privileges = this.ace.getPrivileges();
		String privilegesString = "";
		for(Privilege privilege:privileges){
			privilegesString = privilegesString + privilege.getName() + ",";
		}
		privilegesString = StringUtils.chop(privilegesString);
		return privilegesString;
	}
	public String dumpData() throws RepositoryException{
		StringBuilder sb = new StringBuilder();
		
		sb.append("path: " + this.jcrPath);
		
		sb.append("\n");
		
		Privilege[] privileges = ace.getPrivileges();
		
		String permissionString = PERM_ALLOW;
		if(!((JackrabbitAccessControlEntry)ace).isAllow()){
			permissionString = PERM_DENY;
		}
		sb.append(permissionString + " ");
		
		String privilegesString = "";
		for(Privilege privilege:privileges){
			privilegesString = privilegesString + privilege.getName() + ",";
		}
		privilegesString = StringUtils.chop(privilegesString);
		sb.append("actions: " + CqActionsMapping.getCqActions(privilegesString));
		
		sb.append("\n");
		
		Value repGlob = ((JackrabbitAccessControlEntry)ace).getRestriction(REP_GLOB_RESTRICTION);
		if(repGlob != null){
			sb.append("glob" + ": "+ repGlob.getString());
		}
		sb.append("\n");
		
		return sb.toString();
	}
	public int hashCode() {
	      return this.ace.hashCode();
	  }
	
	
}
