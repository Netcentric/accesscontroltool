/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.el.ArrayELResolver;
import jakarta.el.BeanELResolver;
import jakarta.el.CompositeELResolver;
import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.FunctionMapper;
import jakarta.el.ListELResolver;
import jakarta.el.MapELResolver;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.el.ExpressionFactoryImpl;

/** Evaluates expressions that may contain variables from for loops.
 * 
 * Not an OSGi Service as it carries state and is not multi-threading safe.
 * 
 * @author ghenzler */
public class YamlMacroElEvaluator {

    private ExpressionFactory expressionFactory;
    private ELContext context;

    private Map<? extends Object, ? extends Object> vars = new HashMap<Object, Object>();

    public YamlMacroElEvaluator() {

        expressionFactory = new ExpressionFactoryImpl();
        
        final VariableMapper variableMapper = new ElVariableMapper();
        final ElFunctionMapper functionMapper = new ElFunctionMapper();
        final CompositeELResolver compositeELResolver = new CompositeELResolver();

        compositeELResolver.add(new BaseELResolver());
        compositeELResolver.add(new ArrayELResolver());
        compositeELResolver.add(new ListELResolver());
        compositeELResolver.add(new BeanELResolver());
        compositeELResolver.add(new MapELResolver());
        context = new ELContext() {
            @Override
            public ELResolver getELResolver() {
                return compositeELResolver;
            }

            @Override
            public FunctionMapper getFunctionMapper() {
                return functionMapper;
            }

            @Override
            public VariableMapper getVariableMapper() {
                return variableMapper;
            }

            @Override
            public Object convertToType(Object obj, Class<?> type) {
                if(obj == null) {
                    return null;
                }
                if(type == null) {
                    return obj;
                }
                if(type.equals(String.class)) {
                    return String.valueOf(obj);
                }
                if(type.isAssignableFrom(obj.getClass())) {
                    return obj;
                } else {
                    // no special conversions supported
                    throw new IllegalStateException("Cannot convert "+obj.getClass() +" to " + type + " (object: "+obj+")");
                }
            }
        };
    }

    public <T> T evaluateEl(String el, Class<T> expectedResultType, Map<? extends Object, ? extends Object> variables) {
        
        vars = variables;
        
        ValueExpression expression = expressionFactory.createValueExpression(context, el, expectedResultType);
        T value = (T) expression.getValue(context);
        return value;
    }

    public static class ElFunctionMapper extends FunctionMapper {

        private Map<String, Method> functionMap = new HashMap<String, Method>();

        public ElFunctionMapper() {

            try {
                Method[] exportedMethods = new Method[] {

                        StringUtils.class.getMethod("split", new Class<?>[] { String.class, String.class }),
                        StringUtils.class.getMethod("join", new Class<?>[] { Object[].class, String.class }),
                        ArrayUtils.class.getMethod("subarray", new Class<?>[] { Object[].class, int.class, int.class }),
                        
                        StringUtils.class.getMethod("upperCase", new Class<?>[] { String.class }),
                        StringUtils.class.getMethod("lowerCase", new Class<?>[] { String.class }),
                        StringUtils.class.getMethod("substringAfter", new Class<?>[] { String.class, String.class }),
                        StringUtils.class.getMethod("substringBefore", new Class<?>[] { String.class, String.class }),
                        StringUtils.class.getMethod("substringAfterLast", new Class<?>[] { String.class, String.class }),
                        StringUtils.class.getMethod("substringBeforeLast", new Class<?>[] { String.class, String.class }),
                        StringUtils.class.getMethod("contains", new Class<?>[] { CharSequence.class, CharSequence.class }),
                        StringUtils.class.getMethod("endsWith", new Class<?>[] { CharSequence.class, CharSequence.class }),
                        StringUtils.class.getMethod("startsWith", new Class<?>[] { CharSequence.class, CharSequence.class }),
                        StringUtils.class.getMethod("replace", new Class<?>[] { String.class, String.class, String.class }),
                        StringUtils.class.getMethod("length", new Class<?>[] { CharSequence.class }),
                        StringUtils.class.getMethod("defaultIfEmpty", new Class<?>[] { CharSequence.class, CharSequence.class }),
                        StringEscapeUtils.class.getMethod("escapeXml11", new Class<?>[] { String.class }),

                        YamlMacroElEvaluator.ElFunctionMapper.class.getMethod("containsItem", new Class<?>[] { List.class, String.class }),
                        YamlMacroElEvaluator.ElFunctionMapper.class.getMethod("containsAllItems", new Class<?>[] { List.class, List.class }),
                        YamlMacroElEvaluator.ElFunctionMapper.class.getMethod("containsAnyItem", new Class<?>[] { List.class, List.class }),
                        YamlMacroElEvaluator.ElFunctionMapper.class.getMethod("keys", new Class<?>[] { Map.class }),
                        YamlMacroElEvaluator.ElFunctionMapper.class.getMethod("values", new Class<?>[] { Map.class })
                };
                for (Method method : exportedMethods) {
                    functionMap.put(method.getName(), method);
                }

            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Class StringUtils/ArrayUtils is missing expected methods", e);
            }

        }

        @Override
        public Method resolveFunction(String prefix, String localName) {
            String key = (StringUtils.isNotBlank(prefix) ? prefix + ":" : "") + localName;
            return functionMap.get(key);
        }
        
        //  -- additional functions not available in StringUtils or ArrayUtils
        public static boolean containsItem(List<String> list, String element) {
            return list.contains(element);
        }

        public static boolean containsAllItems(List<String> list, List<String> items) {
            return list.containsAll(items);
        }

        public static boolean containsAnyItem(List<String> list, List<String> items) {
            return !Collections.disjoint(list, items);
        }

        public static List<Object> keys(Map<Object,Object> map) {
            return new ArrayList<>(map.keySet());
        }
        public static List<Object> values(Map<Object,Object> map) {
            return new ArrayList<>(map.values());
        }
    }

    class ElVariableMapper extends VariableMapper {

        @Override
        public ValueExpression resolveVariable(String paramString) {
            Object value = vars.get(paramString);
            if (value == null && paramString.equals("env")) {
                value = System.getenv();
            }
            return value != null ? expressionFactory.createValueExpression(value, value.getClass()) : null;
        }

        @Override
        public ValueExpression setVariable(String paramString, ValueExpression paramValueExpression) {
            throw new UnsupportedOperationException();
        }

    }

    /** extra base resolver needed to allow to put maps on root level, see
     * http://illegalargumentexception.blogspot.com.es/2008/04/java-using-el-outside-j2ee.html */
    class BaseELResolver extends ELResolver {

        private ELResolver delegate = new MapELResolver();

        public BaseELResolver() {
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base == null) {
                base = vars;
            }
            return delegate.getValue(context, base, property);
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            if (base == null) {
                base = vars;
            }
            return delegate.getCommonPropertyType(context, base);
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context,
                Object base) {
            if (base == null) {
                base = vars;
            }
            return delegate.getFeatureDescriptors(context, base);
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            if (base == null) {
                base = vars;
            }
            return delegate.getType(context, base, property);
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            if (base == null) {
                base = vars;
            }
            return delegate.isReadOnly(context, base, property);
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
            if (base == null) {
                base = vars;
            }
            delegate.setValue(context, base, property, value);
        }

    }
    
}
