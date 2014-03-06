package biz.netcentric.cq.tools.actool.authorizableutils;



import org.apache.commons.lang.StringUtils;

public class AuthorizableConfigBean {

	private String principalID;
	private String principalName;
	
	private String[] memberOf;
	private String description;
	private String path;
	private String password;
	private boolean isGroup = true;
	
	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	
	
	public boolean isGroup() {
		return isGroup;
	}
	public void setIsGroup(boolean isGroup) {
		this.isGroup = isGroup;
	}
	public void memberOf(boolean isGroup) {
		this.isGroup = isGroup;
	}

	public String getPrincipalName() {
		return principalName;
	}

	public void setAuthorizableName(String principalName) {
		this.principalName = principalName;
	}
	
	public String getPrincipalID() {
		return principalID;
	}

	public void setPrincipalID(final String principalID) {
		this.principalID = principalID;
	}

	public String[] getMemberOf() {
		return memberOf;
	}
	public boolean isMemberOfOtherGroups(){
		return memberOf != null;
	}
	public String getMemberOfString() {
		if(memberOf == null){
			return "";
		}
		
		StringBuilder memberOfString = new StringBuilder();
		
		for(String group : memberOf){
			memberOfString.append(group).append(",");
		}
		return StringUtils.chop(memberOfString.toString());
	}

	public void setMemberOf(final String[] memberOf) {
		this.memberOf = memberOf;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public String getPath() {
		return path;
	}

	public void setPath(final String path) {
		this.path = path;
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n" +"id: " + this.principalID + "\n");
		sb.append("name: " + this.principalName + "\n");
		sb.append("description: " + this.description + "\n");
		sb.append("path: " + this.path + "\n");
		sb.append("memberOf: " + this.getMemberOfString() + "\n");
		return sb.toString();
	}
}
