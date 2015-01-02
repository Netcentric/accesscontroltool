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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public class YamsConfigReaderTest {

    @Test
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
        List<LinkedHashMap> yamlList = getYamlList("testmacro.yaml");
        System.out.println(yamlList);
        Map<String, LinkedHashSet<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        Map<String, Set<AceBean>> aces = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        System.out.println(aces);
        // TODO: finish this test
    }

    @Test
    public void testForLoopParsing() {
        String loop = "mkt in [ MKT1, MKT2, MKT3 ]";
        String path = "/content/brand/${mkt}";
        YamlConfigReader yamlConfigReader = new YamlConfigReader();
        Map<String, String> config = new HashMap<String, String>();
        config.put("for", loop);
        config.put("path", path);
        List<AceBean> beans = yamlConfigReader.unrollForLoop(config, "groupA");
        System.out.println(beans);
        assertEquals("Number of loop iterations", 3, beans.size());
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
