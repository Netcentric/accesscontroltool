package biz.netcentric.cq.tools.actool.yaml;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class LegacyAcToolYamlEventTransformerTest {

    @Test
    void test() throws IOException {
        YamlTransformer yamlTransformer = new YamlTransformer(Collections.singletonList(new LegacyAcToolYamlEventTransformer()));
        try (InputStream input = this.getClass().getResourceAsStream("test1.yaml")) {
            Objects.requireNonNull(input);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                yamlTransformer.transform(input, "test1.yaml", output);
                assertByteArrayEqualsResource(output.toByteArray(), "test1-v4.yaml");
            }
        }
    }

    void assertByteArrayEqualsResource(byte[] data, String resourceName) throws IOException {
        try (BufferedReader outputReader = new BufferedReader(new StringReader(new String(data, StandardCharsets.UTF_8)));
             InputStream expectedOutput = this.getClass().getResourceAsStream(resourceName);
             BufferedReader expectedOutputReader = new BufferedReader(new InputStreamReader(expectedOutput, StandardCharsets.UTF_8))) {
            Objects.requireNonNull(expectedOutput);
            assertLinesMatch(expectedOutputReader.lines(), outputReader.lines());
        }
    }
}
