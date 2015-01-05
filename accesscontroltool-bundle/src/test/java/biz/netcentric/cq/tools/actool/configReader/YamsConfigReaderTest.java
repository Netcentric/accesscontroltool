/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configReader;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public class YamsConfigReaderTest {

    @Ignore
    public void testTemplatesSection() throws IOException {
        YamlConfigReader yamlConfigReader = new YamlConfigReader();
        List<LinkedHashMap> yamlList = getYamlList("test-templates.yaml");
        assertEquals("Number of top-level config elements", 3, yamlList.size());
        Map<String, String> mappings = yamlConfigReader.getTemplateConfiguration(yamlList);
        assertEquals("/apps/mysite/templates/home", mappings.get("/content/site/*"));
        assertEquals("/apps/mysite/templates/firstlevel", mappings.get("/content/site/en/*"));
        assertEquals("/apps/mysite/templates/secondlevel", mappings.get("/content/site/en/*/*"));
    }

    @Test
    public void testLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {
        YamlConfigReader yamlConfigReader = new YamlConfigReader();
        List<LinkedHashMap> yamlList = getYamlList("test-loop.yaml");
        Map<String, LinkedHashSet<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        Map<String, Set<AceBean>> aces = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        assertEquals("Number of groups", 5, aces.size());
        Set<AceBean> group1 = aces.get("content-BRAND-MKT1-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        Set<AceBean> group2 = aces.get("content-BRAND-MKT2-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());
    }

    @Test
    public void testForLoopParsing() {
        String forStmt = "for brand IN [ BRAND1, BRAND2, BRAND3 ]";
        YamlConfigReader yamlConfigReader = new YamlConfigReader();
        List<Map<String, ?>> groups = new LinkedList<Map<String, ?>>();
        Map<String, List<?>> reader = new HashMap<String, List<?>>();
        List<Map<String, String>> readerAces = new LinkedList<Map<String, String>>(); 
        reader.put("content-${brand}-reader", readerAces);
        Map<String, String> ace1 = new HashMap<String, String>();
        ace1.put("path", "/content/${brand}");
        readerAces.add(ace1);
        Map<String, String> ace2 = new HashMap<String, String>();
        ace2.put("path", "/content/${brand}/foo");
        readerAces.add(ace2);
        groups.add(reader);
        List<AceBean> beans = yamlConfigReader.unrollAceForLoop(forStmt, groups);
        assertEquals("Number of loop iterations", 6, beans.size());
        assertEquals("/content/BRAND1", beans.get(0).getJcrPath());
        assertEquals("/content/BRAND1/foo", beans.get(1).getJcrPath());
        assertEquals("/content/BRAND2", beans.get(2).getJcrPath());
        assertEquals("/content/BRAND2/foo", beans.get(3).getJcrPath());
        assertEquals("/content/BRAND3", beans.get(4).getJcrPath());
        assertEquals("/content/BRAND3/foo", beans.get(5).getJcrPath());
    }
    
    private List<LinkedHashMap> getYamlList(String filename) throws IOException {
        String configString = getTestConfigAsString(filename);

        Yaml yaml = new Yaml();
        List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml
                .load(configString);
        return yamlList;
    }

    private String getTestConfigAsString(final String resourceName)
            throws IOException {
        ClassLoader classloader = Thread.currentThread()
                .getContextClassLoader();
        InputStream is = classloader.getResourceAsStream(resourceName);

        StringWriter stringWriter = new StringWriter();
        IOUtils.copy(is, stringWriter, "UTF-8");
        return stringWriter.toString();
    }

}
