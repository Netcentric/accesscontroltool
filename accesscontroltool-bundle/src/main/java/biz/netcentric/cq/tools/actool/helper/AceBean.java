package biz.netcentric.cq.tools.actool.helper;

import java.util.Arrays;

import org.apache.commons.lang.StringUtils;


/**
 * 
 * @author jochenkoschorke
 * This class is used to store data of an AcessControlEntry. Objects of this class get created 
 * during the reading of the configuration file in order to set the corresponding ACEs in the system on the one hand
 * and to store data during the reading of existing ACEs before writing the data back to a dump or 
 * configuration file again on the other hand.
 */
public class AceBean {
	
	private String jcrPath;
	private boolean isAllow;
	private String repGlob;
	private String actionsString;
	private String privilegesString;
	private String principal;
	private boolean isAllowProvided = false;
	private String[] actions;
	
	public void clearActions(){
		this.actions = null;
		this.actionsString = "";
	}
	public String getPrincipalName() {
		return principal;
	}
	public void setPrincipal(String principal) {
		this.principal = principal;
	}
	public String getJcrPath() {
		return jcrPath;
	}
	public void setJcrPath(String jcrPath) {
		this.jcrPath = jcrPath;
	}
	public boolean isAllow() {
		return isAllow;
	}
	public String getPermission(){
		return (this.isAllow) ? "allow" : "deny";
	}
	public boolean getPermissionAsBoolean(){
		return (this.isAllow) ? true : false;
	}
	public void setAllow(boolean isAllow) {
		this.isAllow = isAllow;
	}
	public void setIsAllowProvide(boolean bool){
		this.isAllowProvided = bool;
	}
	public boolean getIsAllowProvide(){
		return this.isAllowProvided;
	}
	public String getRepGlob() {
		if(this.repGlob == null){
			return "";
		}
		return repGlob;
	}
	public void setRepGlob(String repGlob) {
		if(repGlob == null){
			repGlob = "";
		}
		this.repGlob = repGlob;
	}
	public String getActionsString() {
		if(this.actions != null){
			StringBuilder sb = new StringBuilder();
			for(String action : this.actions){
				sb.append(action).append(",");
			}
			return StringUtils.chomp(sb.toString(), ",");
		}
		return "";
	}
	public void setActions(String[] actions){
		this.actions = actions;
	}
	public void setActionsString(String actionsString) {
		this.actionsString = actionsString;
	}
	public String[] getActions(){
		return this.actions;
	}
	public String getPrivilegesString() {
		return privilegesString;
	}
	public String[] getPrivileges() {
		if(StringUtils.isNotBlank(privilegesString)){
			return privilegesString.split(",");
		}
		return null;
	}
	public void setPrivilegesString(String privilegesString) {
		this.privilegesString = privilegesString;
	}
	
	@Override
	public String toString() {
		return "AceBean [jcrPath=" + jcrPath + "\n" +", isAllow=" + isAllow + "\n"
				+ ", repGlob=" + repGlob +  "\n"+ ", actionsString=" + actionsString + "\n"
				+ ", privilegesString=" + privilegesString + "\n" +", principal="
				+ principal + "\n" +  ", isAllowProvided=" + isAllowProvided
				+ "\n" + ", actions=" + Arrays.toString(actions) + "]";
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(actions);
		result = prime * result
				+ ((actionsString == null) ? 0 : actionsString.hashCode());
		result = prime * result + (isAllow ? 1231 : 1237);
		result = prime * result + (isAllowProvided ? 1231 : 1237);
		result = prime * result + ((jcrPath == null) ? 0 : jcrPath.hashCode());
		result = prime * result
				+ ((principal == null) ? 0 : principal.hashCode());
		result = prime
				* result
				+ ((privilegesString == null) ? 0 : privilegesString.hashCode());
		result = prime * result + ((repGlob == null) ? 0 : repGlob.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AceBean other = (AceBean) obj;
		if (!Arrays.equals(actions, other.actions))
			return false;
		if (actionsString == null) {
			if (other.actionsString != null)
				return false;
		} else if (!actionsString.equals(other.actionsString))
			return false;
		if (isAllow != other.isAllow)
			return false;
		if (isAllowProvided != other.isAllowProvided)
			return false;
		if (jcrPath == null) {
			if (other.jcrPath != null)
				return false;
		} else if (!jcrPath.equals(other.jcrPath))
			return false;
		if (principal == null) {
			if (other.principal != null)
				return false;
		} else if (!principal.equals(other.principal))
			return false;
		if (privilegesString == null) {
			if (other.privilegesString != null)
				return false;
		} else if (!privilegesString.equals(other.privilegesString))
			return false;
		if (repGlob == null) {
			if (other.repGlob != null)
				return false;
		} else if (!repGlob.equals(other.repGlob))
			return false;
		return true;
	}
	
}
	

