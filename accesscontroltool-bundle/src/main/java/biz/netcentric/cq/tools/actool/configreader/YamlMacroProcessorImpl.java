/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Session;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.history.AcInstallationLog;

@Service
@Component
public class YamlMacroProcessorImpl implements YamlMacroProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(YamlMacroProcessorImpl.class);

    private final Pattern forLoopPattern = Pattern.compile("for +(\\w+) +in +(?:\\[([,/\\s\\w\\-]+)\\]|children +of +([^\\s]+))",
            Pattern.CASE_INSENSITIVE);
    private final Pattern ifPattern = Pattern.compile("if +(\\$\\{[^\\}]+\\})", Pattern.CASE_INSENSITIVE);

    private final Pattern variableDefPattern = Pattern.compile("DEF +([a-z0-9]+)=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    YamlMacroElEvaluator elEvaluator = new YamlMacroElEvaluator();

    @Reference
    YamlMacroChildNodeObjectsProvider yamlMacroChildNodeObjectsProvider;

    @Override
    public List<LinkedHashMap> processMacros(List<LinkedHashMap> yamlList, AcInstallationLog installLog, Session session) {
        return (List<LinkedHashMap>) transform(yamlList, installLog, session);
    }

    private Object transform(Object o, AcInstallationLog installLog, Session session) {
        return transform(o, new HashMap<String, Object>(), installLog, session);
    }

    private Object transform(Object o, Map<String, Object> variables, AcInstallationLog installLog, Session session) {
        if (o == null) {
            return null;
        } else if (o instanceof String) {
            String str = (String) o;

            Matcher variableDefMatcher = variableDefPattern.matcher(str);
            if (variableDefMatcher.find()) {
                String varName = variableDefMatcher.group(1);
                String varValue = variableDefMatcher.group(2);
                String varValueEvaluated = elEvaluator.evaluateEl(varValue, String.class, variables);
                variables.put(varName, varValueEvaluated);
                return null;
            }

            String result = elEvaluator.evaluateEl(str, String.class, variables);
            return result;

        } else if (o instanceof Boolean) {
            return (Boolean) o;

        } else if (o instanceof List) {
            List list = (List) o;
            List transformedList = new LinkedList();
            for (Object val : list) {
                Object transformedObject = transform(val, variables, installLog, session);
                addToListWithPotentialUnfolding(transformedList, transformedObject);
            }
            return transformedList;
        } else if (o instanceof Map) {
            Map map = (Map) o;
            Map resultMap = new LinkedHashMap();
            for (Object key : map.keySet()) {
                Object objVal = map.get(key);

                Matcher forMatcher = forLoopPattern.matcher(key.toString());
                if (forMatcher.matches()) {
                    // map is skipped and value returned directly
                    return evaluateForStatement(variables, objVal, forMatcher, installLog, session);
                }

                Matcher ifMatcher = ifPattern.matcher(key.toString());
                if (ifMatcher.matches()) {
                    // map is skipped and value returned directly
                    return evaluateIfStatement(variables, objVal, ifMatcher, installLog, session);
                }

                // default: transform both key and value
                Object transformedKey = transform(key, variables, installLog, session);
                Object transformedVal = transform(objVal, variables, installLog, session);
                if (transformedVal != null) {
                    resultMap.put(transformedKey, transformedVal);
                }

            }
            return resultMap;
        } else {
            throw new IllegalStateException("Unexpected class " + o.getClass() + " in object structure produced by yaml: " + o);

        }
    }

    private Object evaluateForStatement(Map<String, Object> variables, Object objVal, Matcher forMatcher,
            AcInstallationLog installLog, Session session) {
        String varName = StringUtils.trim(forMatcher.group(1));
        String valueOfInClause = StringUtils.trim(forMatcher.group(2));
        String pathOfChildrenOfClause = StringUtils.trim(forMatcher.group(3));
        // allow variables in root path also
        if (StringUtils.isNotBlank(pathOfChildrenOfClause)) {
            pathOfChildrenOfClause = elEvaluator.evaluateEl(pathOfChildrenOfClause, String.class, variables);
        }

        final List<?> iterationValues = valueOfInClause != null ? Arrays.asList(valueOfInClause.split("\\s*,\\s*"))
                : yamlMacroChildNodeObjectsProvider.getValuesForPath(pathOfChildrenOfClause, installLog, session);

        List toBeUnfoldedList = unfoldLoop(variables, objVal, varName, iterationValues, installLog, session);

        return toBeUnfoldedList;
    }

    private Object evaluateIfStatement(Map<String, Object> variables, Object objVal, Matcher ifMatcher,
            AcInstallationLog installLog, Session session) {
        String condition = ifMatcher.group(1).trim();

        boolean expressionIsTrue = elEvaluator.evaluateEl(condition, Boolean.class, variables);

        List toBeUnfoldedList = unfoldIf(variables, objVal, expressionIsTrue, installLog, session);

        return toBeUnfoldedList;
    }

    private void addToListWithPotentialUnfolding(List transformedList, Object transformedObject) {
        if (transformedObject == null) {
            return; // this happens for vars with DEF - those are evaluated already, entry must be left out
        } else if (transformedObject instanceof ToBeUnfoldedList) {
            // add entries individually (for for loops)
            ToBeUnfoldedList toBeUnfoldedList = (ToBeUnfoldedList) transformedObject;
            for (Object object : toBeUnfoldedList) {
                transformedList.add(object);
            }
        } else {
            // add transformed object as is
            transformedList.add(transformedObject);
        }
    }

    private List unfoldLoop(Map<String, Object> variables, Object val, String varName, List<?> varValues,
            AcInstallationLog installLog, Session session) {
        List resultList = new ToBeUnfoldedList();

        for (Object varValue : varValues) {
            Map<String, Object> variablesAtThisScope = new HashMap<String, Object>(variables);
            variablesAtThisScope.put(varName, varValue);
            unfold(val, resultList, variablesAtThisScope, installLog, session);

        }
        return resultList;
    }

    private List unfoldIf(Map<String, Object> variables, Object val, boolean expressionIsTrue,
            AcInstallationLog installLog, Session session) {
        List resultList = new ToBeUnfoldedList();
        if (expressionIsTrue) {
            unfold(val, resultList, variables, installLog, session);
        } // otherwise return empty list

        return resultList;
    }

    private void unfold(Object val, List resultList, Map<String, Object> variablesAtThisScope,
            AcInstallationLog installLog, Session session) {
        if (val instanceof List) {
            List origList = (List) val;
            for (Object origListItem : origList) {
                Object transformedListItem = transform(origListItem, variablesAtThisScope, installLog, session);
                addToListWithPotentialUnfolding(resultList, transformedListItem);
            }
        } else {
            Object transformedListItem = transform(val, variablesAtThisScope, installLog, session);
            addToListWithPotentialUnfolding(resultList, transformedListItem);
        }
    }

    // marker class
    private class ToBeUnfoldedList extends LinkedList {

    }

    public static void main(String[] args) throws Exception {
        Map<Object, Object> userMap = new HashMap<Object, Object>();
        userMap.put("x", new Integer(123));
        userMap.put("y", new Integer(456));
        userMap.put("TEST", "a long test value");

        String expr = "x= ---- ${upperCase(splitByWholeSeparator(TEST,'long')[1])}";
        String val = new YamlMacroElEvaluator().evaluateEl(expr, String.class, userMap);
        System.out.println("the value for " + expr + " =>> " + val);

    }

}
