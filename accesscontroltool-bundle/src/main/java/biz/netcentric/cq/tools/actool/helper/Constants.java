package biz.netcentric.cq.tools.actool.helper;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Constants {
	
	private Constants(){}
	
	public static final String GROUP_CONFIGURATION_KEY = "group_config";
	public static final String USER_CONFIGURATION_KEY = "user_config";
	public static final String ACE_CONFIGURATION_KEY = "ace_config";
	public static final String LEGACY_ACE_DUMP_SECTION_KEY = "legacy_aces";
	public static final String ACE_SERVLET_PATH = "/bin/ubs/isp/acl";
	
	
	
	public static final Set<String> VALID_CONFIG_SECTION_IDENTIFIERS = new HashSet<String>(Arrays.asList(GROUP_CONFIGURATION_KEY, USER_CONFIGURATION_KEY, ACE_CONFIGURATION_KEY)); 
		
}
