package biz.netcentric.cq.tools.actool.configReader;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public class YamsConfigReaderTest {

    @Test
    public void testTemplatesSection() throws IOException {
        YamlConfigReader yamlConfigReader = new YamlConfigReader();
        List<LinkedHashMap> yamlList = getYamlList();
        assertEquals("Number of top-level config elements", 3, yamlList.size());
        Map<String, String> mappings = yamlConfigReader.getTemplateConfiguration(yamlList);
        assertEquals("/apps/mysite/templates/home", mappings.get("/content/site/*"));
        assertEquals("/apps/mysite/templates/firstlevel", mappings.get("/content/site/en/*"));
        assertEquals("/apps/mysite/templates/secondlevel", mappings.get("/content/site/en/*/*"));
    }

    private List<LinkedHashMap> getYamlList() throws IOException {
        String configString = getTestConfigAsString("test-templates.yaml");

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
