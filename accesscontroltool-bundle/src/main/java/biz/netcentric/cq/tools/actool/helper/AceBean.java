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
	
}
	

