package biz.netcentric.cq.tools.actool.authorizableutils;


import java.util.HashSet;
import java.util.Set;

public class AuthorizableInstallationHistory {

	private Set<String> newCreatedAuthorizables = new HashSet<String>();
	private Set<AuthorizableBean> authorizables = new HashSet<AuthorizableBean>();
	
	public void addAuthorizable(String name, String id, String path, Set<String> members){
		authorizables.add(new AuthorizableBean(members, name, id, path));
	}
	public  void addNewCreatedAuthorizabe(String id){
		 this.newCreatedAuthorizables.add(id);
	}
	public Set<String> getNewCreatedAuthorizables(){
		return this.newCreatedAuthorizables;
	}
	public Set<AuthorizableBean> getAuthorizableBeans(){
		return this.authorizables;
		
	}
}
