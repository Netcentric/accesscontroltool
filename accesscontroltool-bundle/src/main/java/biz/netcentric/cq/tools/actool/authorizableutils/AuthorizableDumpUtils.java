package biz.netcentric.cq.tools.actool.authorizableutils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.servlet.ServletOutputStream;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;

public class AuthorizableDumpUtils {
	
	private static final int BUFSIZE = 4096;
	private static final Logger LOG = LoggerFactory.getLogger(AuthorizableDumpUtils.class);
	
	public static Set<AuthorizableConfigBean> returnGroupBeans(Session session) throws AccessDeniedException, UnsupportedRepositoryOperationException, RepositoryException{
		JackrabbitSession js = (JackrabbitSession) session;
		UserManager userManager = js.getUserManager();
		
		Set <String> groups = QueryHelper.getGroupsFromHome(session);
		Set <AuthorizableConfigBean> userBeans = new LinkedHashSet<AuthorizableConfigBean>();
		
		for(String groupId : groups){
		
			Group group = (Group)userManager.getAuthorizable(groupId);
			AuthorizableConfigBean bean = new AuthorizableConfigBean();
			
			bean.setPrincipalID(group.getID());
			Iterator <Group> it = group.declaredMemberOf();
			List<String> memberOfList = new ArrayList<String>();
			while(it.hasNext()){
				memberOfList.add(it.next().getID());
			}
			bean.setMemberOf(memberOfList.toArray(new String[memberOfList.size()]));
			bean.setIsGroup(group.isGroup());
			bean.setPath(group.getPath());
			
			userBeans.add(bean);
		}
		return userBeans;
		
	}
	
	public static void returnAuthorizableDumpAsFile(final SlingHttpServletResponse response,
			Set<AuthorizableConfigBean> authorizableSet) throws IOException{
		String mimetype =  "application/octet-stream";
		response.setContentType(mimetype);
		ServletOutputStream outStream = null;
		try {
			outStream = response.getOutputStream();
		} catch (IOException e) {
			LOG.error("Exception in AuthorizableDumpUtils: {}", e);
		}

		String fileName = "Authorizable_Dump_" + new Date(System.currentTimeMillis());
		response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

		byte[] byteBuffer = new byte[BUFSIZE];

		try {
			writeAuthorizableConfigToStream(authorizableSet, outStream);
		} catch (IOException e) {
			LOG.error("Exception in AuthorizableDumpUtils: {}", e);
		}
		outStream.close();
	}

	public static void writeAuthorizableConfigToStream(
			Set<AuthorizableConfigBean> authorizableSet,
			ServletOutputStream outStream) throws IOException {
		
		outStream.println("- " + Constants.GROUP_CONFIGURATION_KEY + ":") ;
		outStream.println();
		
		for(AuthorizableConfigBean bean:authorizableSet){
			outStream.println(Constants.DUMP_INDENTATION_KEY + "- " + bean.getPrincipalID() + ":");
			outStream.println();
			outStream.println(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- name: ");
			outStream.println(Constants.DUMP_INDENTATION_PROPERTY + "memberOf: " + bean.getMemberOfString());
			outStream.println(Constants.DUMP_INDENTATION_PROPERTY + "path: " + bean.getPath());
			outStream.println(Constants.DUMP_INDENTATION_PROPERTY + "isGroup: " + "'" + bean.isGroup() + "'");
			outStream.println();
		}
	}
		
   public static StringBuilder getAuthorizableConfigAsString(final StringBuilder sb, final Set<AuthorizableConfigBean> authorizableSet){
	   sb.append("- " + Constants.GROUP_CONFIGURATION_KEY + ":").append("\n");
	   sb.append("\n");
		
		for(AuthorizableConfigBean bean:authorizableSet){
			sb.append(Constants.DUMP_INDENTATION_KEY + "- " + bean.getPrincipalID() + ":").append("\n");
			sb.append("\n");
			sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- name: ").append("\n");
			sb.append(Constants.DUMP_INDENTATION_PROPERTY + "memberOf: " + bean.getMemberOfString()).append("\n");
			sb.append(Constants.DUMP_INDENTATION_PROPERTY + "path: " + bean.getPath()).append("\n");
			sb.append(Constants.DUMP_INDENTATION_PROPERTY + "isGroup: " + "'" + bean.isGroup() + "'").append("\n");
			sb.append("\n");
		}
		return sb;
   }
	
	
	
}
