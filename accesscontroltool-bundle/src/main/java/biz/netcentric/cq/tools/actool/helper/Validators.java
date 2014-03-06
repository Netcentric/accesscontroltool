package biz.netcentric.cq.tools.actool.helper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;

public class Validators {

	private static final Logger LOG = LoggerFactory.getLogger(Validators.class);

	private static final Pattern AUTHORIZABLE_PATTERN =
			Pattern.compile("([a-zA-Z0-9-]+)");

	public static boolean isValidNodePath(final String path){
		if(StringUtils.isBlank(path)){
			return false;
		}
		// TO DO: proper validation
		if ((path == null) || (path.equals(""))) {
			return false;
		}
		return true;
	}

	public static boolean isValidAuthorizableName(final String name){
		if(StringUtils.isBlank(name)){
			return false;
		}
		boolean isValid = false;

		Matcher matcher = AUTHORIZABLE_PATTERN.matcher(name);
		if(matcher.matches()){
			isValid = true;
		}
		return isValid;
	}

	public static boolean isValidRegex(String expression) {
		if(StringUtils.isBlank(expression)){
			return true;
		}
		boolean isValid = true;

			if(expression.startsWith("*")){
				expression = expression.replaceFirst("\\*", "\\\\*");
			}
			try {
				Pattern.compile(expression);
			} catch (PatternSyntaxException e) {
				LOG.error("Error while validating rep glob: {} ", expression, e);
				isValid=false;
			}
		
		return isValid;
	}

	public static boolean isValidAction(String action){
		if(action == null){
			return false;
		}

		if(!CqActionsMapping.map.keySet().contains(action)){
			return false;
		}

		return true;
	}
	public static boolean isValidJcrPrivilege(String privilege){
		if(privilege == null){
			return false;
		}

		if(!CqActionsMapping.getJcrAggregatedPrivilegesList().contains(privilege) && !CqActionsMapping.getJcrAllPrivilegesList().contains(privilege)
				&& !CqActionsMapping.map.get("rep:write").contains(privilege) && !CqActionsMapping.map.get("jcr:write").contains(privilege) ){
			return false;
		}

		return true;
	}
	public static boolean isValidPermission(String permission){
		if(permission == null){
			return false;
		}

		if (StringUtils.equals("allow", permission) || StringUtils.equals("deny", permission) ){
			return true;
		}
		return false;
	}
}
