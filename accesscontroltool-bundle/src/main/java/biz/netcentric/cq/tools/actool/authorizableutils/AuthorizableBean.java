package biz.netcentric.cq.tools.actool.authorizableutils;


import java.util.HashSet;
import java.util.Set;

public class AuthorizableBean {
	private Set<String> members = new HashSet<String>();
	private String name;
	private String id;
	private String path;
	
	public AuthorizableBean(Set<String> members, String name,
			String id, String path) {
		super();
		this.members = members;
		this.name = name;
		this.id = id;
		this.path = path;
	}
	
	public void addMembers(Set<String>members){
		this.members = members;
	}

	public Set<String> getAuthorizablesSnapshot() {
		return members;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id:" + this.id + "\n");
		sb.append("path:" + this.path + "\n");
		sb.append("members:" + this.members.toString() + "\n");
		return super.toString();
	}
}
