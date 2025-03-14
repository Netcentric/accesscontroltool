package biz.netcentric.cq.tools.actool.yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class YamlTransformerTest {

    @Test
    void test() throws IOException {
        YamlTransformer yamlTransformer = new YamlTransformer();
        try (InputStream input = this.getClass().getResourceAsStream("test1.yaml")) {
            Objects.requireNonNull(input);
            String transformed = yamlTransformer.transform(input);
            System.out.println(transformed);
        }
        
    }

}
