/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.jcr.RepositoryException;
import javax.jcr.security.AccessControlManager;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.security.util.CqActions;

public class Validators {

    private static final Logger LOG = LoggerFactory.getLogger(Validators.class);

    public static boolean isValidNodePath(final String path) {
        if (StringUtils.isBlank(path)) {
            return true; // repository level permissions are created with 'left-out' path property
        }

        if (!path.startsWith("/")) {
            return false;
        }
        return true;
    }

    /**
     * Validates in the same way as <a href="https://github.com/apache/jackrabbit-oak/blob/7999b5cbce87295b502ea4d1622e729f5b96701d/oak-core/src/main/java/org/apache/jackrabbit/oak/security/user/UserManagerImpl.java#L421">Oak's UserManagerImpl</a>.
     * @param id the authorizable id to validate
     * @return {@code true} in case the given id is a valid authorizable id, otherwise {@code false}
     */
    public static boolean isValidAuthorizableId(final String id) {
        if (StringUtils.isBlank(id)) {
            return false;
        }
        return true;
    }

    public static boolean isValidRegex(String expression) {
        if (StringUtils.isBlank(expression)) {
            return true;
        }
        boolean isValid = true;

        if (expression.startsWith("*")) {
            expression = expression.replaceFirst("\\*", "\\\\*");
        }
        try {
            Pattern.compile(expression);
        } catch (PatternSyntaxException e) {
            LOG.error("Error while validating rep glob: {} ", expression, e);
            isValid = false;
        }

        return isValid;
    }

    public static boolean isValidAction(String action) {
    	List<String> validActions = Arrays.asList(CqActions.ACTIONS);
        if (action == null) {
            return false;
        }

        if (!validActions.contains(action)) {
            return false;
        }

        return true;
    }

    public static boolean isValidJcrPrivilege(String privilege, AccessControlManager aclManager) {
        if (privilege == null) {
            return false;
        }
        try {
			aclManager.privilegeFromName(privilege);
		} catch (RepositoryException e) {
			return false;
		}
        return true;
    }

    public static boolean isValidPermission(String permission) {
        if (permission == null) {
            return false;
        }

        if (StringUtils.equals("allow", permission)
                || StringUtils.equals("deny", permission)) {
            return true;
        }
        return false;
    }
}
