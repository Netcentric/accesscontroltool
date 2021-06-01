/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

import org.slf4j.helpers.MessageFormatter;

@Component
public class YamlMacroProcessorImpl implements YamlMacroProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(YamlMacroProcessorImpl.class);

    private static final Pattern FOR_LOOP_PATTERN = Pattern.compile(
            "for +(\\w+)( +with +content)? +in +(?:\\[([,/\\s\\w\\-\\.:]+)\\]|children +of +([^\\s]+)|(\\$\\{[^\\}]+\\}))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IF_PATTERN = Pattern.compile("if +(\\$\\{[^\\}]+\\})", Pattern.CASE_INSENSITIVE);

    private static final String VARIABLE_DEF_BASE_PATTERN = "DEF +([a-z0-9_]+)=";
    static final Pattern VARIABLE_DEF_PATTERN_COMPLEX_VAL_FROM_YAML = Pattern.compile(VARIABLE_DEF_BASE_PATTERN, Pattern.CASE_INSENSITIVE);
    static final Pattern VARIABLE_DEF_PATTERN_ONE_LINE = Pattern.compile(VARIABLE_DEF_BASE_PATTERN+"(?:\\[(.+)\\]|(\"?)([^\"]*)(\\3))",
            Pattern.CASE_INSENSITIVE);

    static final String COMMA_SEPARATED_LIST_SPLITTER = "\\s*,\\s*";

    YamlMacroElEvaluator elEvaluator = new YamlMacroElEvaluator();

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    YamlMacroChildNodeObjectsProvider yamlMacroChildNodeObjectsProvider;


    
    @Override
    public List<Map> processMacros(List<Map> yamlList, Map<String, Object> globalVariables, InstallationLogger installLog, Session session) {
        Map<String, Object> initialVariables = getLocalVariables(yamlList, globalVariables, installLog, session);
        return (List<Map>) transform(yamlList, initialVariables, installLog, session);
    }

    private Map<String, Object> getLocalVariables(List<Map> yamlList, Map<String, Object> globalVariables,
            InstallationLogger installLog, Session session) {

        Set<String> initalGlobalVarNames = new HashSet<>(globalVariables.keySet());
        
        // read variables that might be defined in global_config to global variables
        Iterator<?> topLevelIterator = yamlList.iterator();
        Object transformedGlobalConfig = null;
        while (topLevelIterator.hasNext()) {
            Object obj = topLevelIterator.next();
            if(obj instanceof Map) {
                Map map = (Map) obj;
                if(!map.isEmpty() && Constants.GLOBAL_CONFIGURATION_KEY.equals(map.keySet().iterator().next())) {
                    transformedGlobalConfig = transform(map, globalVariables, installLog, session);
                    topLevelIterator.remove();
                    break;
                }
            }
        }
        if(transformedGlobalConfig != null) {
            yamlList.add(0, (Map) transformedGlobalConfig);
        }
        
        for (Entry<String,Object> globalVar : globalVariables.entrySet()) {
            if(!initalGlobalVarNames.contains(globalVar.getKey())) {
                installLog.addVerboseMessage(LOG, "Global DEF Statement: "+globalVar.getKey() + "="+globalVar.getValue());
            }
        }

        Map<String, Object> localVariables = new LinkedHashMap<String, Object>();
        localVariables.putAll(globalVariables);
        return localVariables;
    }

    private Object transform(Object o, Map<String, Object> variables, InstallationLogger installLog, Session session) {
        if (o == null) {
            return null;
        } else if (o instanceof String) {
            String str = (String) o;

            Matcher variableDefMatcher = VARIABLE_DEF_PATTERN_ONE_LINE.matcher(str);
            if (variableDefMatcher.find()) {
                return evaluateDefStatementOneLine(variables, variableDefMatcher, installLog);
            }

            Object result = elEvaluator.evaluateEl(str, Object.class, variables);
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

                String string = key.toString();
                Matcher forMatcher = FOR_LOOP_PATTERN.matcher(string);
                if (forMatcher.matches()) {
                    // map is skipped and value returned directly
                    return evaluateForStatement(variables, objVal, forMatcher, installLog, session);
                }

                Matcher ifMatcher = IF_PATTERN.matcher(string);
                if (ifMatcher.matches()) {
                    // map is skipped and value returned directly
                    return evaluateIfStatement(variables, objVal, ifMatcher, installLog, session);
                }
                
                Matcher complexVarDefMatcher = VARIABLE_DEF_PATTERN_COMPLEX_VAL_FROM_YAML.matcher(string);
                if (complexVarDefMatcher.matches()) {
                    // map is skipped and value returned directly
                    return evaluateDefStatementComplex(variables, complexVarDefMatcher, objVal, installLog);
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

    private Object evaluateDefStatementOneLine(Map<String, Object> variables, Matcher variableDefMatcher, InstallationLogger installLog) {
        String varName = variableDefMatcher.group(1);
        String varValueArr = variableDefMatcher.group(2);
        String varValueStr = variableDefMatcher.group(4);
        
        Object varValueEvaluated;
        if (varValueStr != null) {
            varValueEvaluated = elEvaluator.evaluateEl(varValueStr, Object.class, variables);
        } else if (varValueArr != null) {
            List<Object> result = new ArrayList<Object>();
            
            String[] arrayVals = varValueArr.split(COMMA_SEPARATED_LIST_SPLITTER);
            for (String arrayVal : arrayVals) {
                Object arrayValEvaluated = elEvaluator.evaluateEl(arrayVal, Object.class, variables);
                result.add(arrayValEvaluated);
            }
            varValueEvaluated = result;
        } else {
            throw new IllegalStateException("None of the def value types were set even though RegEx matched");
        }
        
        if(variables.containsKey(varName)) {
            installLog.addVerboseMessage(LOG, "Overwriting variable '"+varName + "' with "+varValueEvaluated);
        }
        variables.put(varName, varValueEvaluated);
        return null;
    }

    private Object evaluateDefStatementComplex(Map<String, Object> variables, Matcher variableDefMatcher, Object varComplexValueFromYaml, InstallationLogger installLog) {
        String varName = variableDefMatcher.group(1);
        if(variables.containsKey(varName)) {
            installLog.addVerboseMessage(LOG, "Overwriting variable '"+varName + "' with "+varComplexValueFromYaml);
        }
        variables.put(varName, varComplexValueFromYaml);
        return null;
    }

    private Object evaluateForStatement(Map<String, Object> variables, Object objVal, Matcher forMatcher,
            InstallationLogger installLog, Session session) {
        String varName = StringUtils.trim(forMatcher.group(1));
        String withClause = StringUtils.trim(forMatcher.group(2));
        String valueOfInClause = StringUtils.trim(forMatcher.group(3));
        String pathOfChildrenOfClause = StringUtils.trim(forMatcher.group(4));
        String variableForInClause = StringUtils.trim(forMatcher.group(5));

        List<?> iterationValues;
        if(valueOfInClause != null) {
            iterationValues = Arrays.asList(valueOfInClause.split(COMMA_SEPARATED_LIST_SPLITTER));
        } else if(pathOfChildrenOfClause!=null) {
            // allow variables in root path also
            pathOfChildrenOfClause = elEvaluator.evaluateEl(pathOfChildrenOfClause, String.class, variables);
            iterationValues = yamlMacroChildNodeObjectsProvider.getValuesForPath(pathOfChildrenOfClause, installLog, session, StringUtils.isNotBlank(withClause));
        } else if(variableForInClause!=null) {
            iterationValues = elEvaluator.evaluateEl(variableForInClause, List.class, variables);
            if(iterationValues == null) {
                if(variableForInClause.contains(".") || variableForInClause.contains("[")) {
                    return null;
                } else {
                    throw new IllegalStateException("LOOP over EL ${"+variableForInClause+"} is null");
                }
                
            }
        } else {
            throw new IllegalStateException("None of the loop type variables were set even though RegEx matched");
        }

        List toBeUnfoldedList = unfoldLoop(variables, objVal, varName, iterationValues, installLog, session);

        return toBeUnfoldedList;
    }

    private Object evaluateIfStatement(Map<String, Object> variables, Object objVal, Matcher ifMatcher,
            InstallationLogger installLog, Session session) {
        String condition = ifMatcher.group(1).trim();

        Boolean expressionIsTrue = elEvaluator.evaluateEl(condition, Boolean.class, variables);

        if (expressionIsTrue == null) {
            installLog.addWarning(LOG, MessageFormatter.format("Expression {} evaluates to null, returning false", condition).getMessage());
            expressionIsTrue = false;
        }

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
            InstallationLogger installLog, Session session) {
        List resultList = new ToBeUnfoldedList();

        for (Object varValue : varValues) {
            Map<String, Object> variablesAtThisScope = new HashMap<String, Object>(variables);
            variablesAtThisScope.put(varName, varValue);
            unfold(val, resultList, variablesAtThisScope, installLog, session);

        }
        return resultList;
    }

    private List unfoldIf(Map<String, Object> variables, Object val, boolean expressionIsTrue,
            InstallationLogger installLog, Session session) {
        List resultList = new ToBeUnfoldedList();
        if (expressionIsTrue) {
            unfold(val, resultList, variables, installLog, session);
        } // otherwise return empty list

        return resultList;
    }

    private void unfold(Object val, List resultList, Map<String, Object> variablesAtThisScope,
            InstallationLogger installLog, Session session) {
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
