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

    private static final Pattern GROUP_ID_PATTERN = Pattern
            .compile("([a-zA-Z0-9-_. ]+)");

    public static boolean isValidNodePath(final String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        // TO DO: proper validation
        if ((path == null) || (path.equals(""))) {
            return false;
        }
        return true;
    }

    public static boolean isValidAuthorizableId(final String name) {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        boolean isValid = false;

        Matcher matcher = GROUP_ID_PATTERN.matcher(name);
        if (matcher.matches()) {
            isValid = true;
        }
        return isValid;
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
