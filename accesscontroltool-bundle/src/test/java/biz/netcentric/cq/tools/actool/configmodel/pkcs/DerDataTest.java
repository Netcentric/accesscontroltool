package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test the different encodings in https://tools.ietf.org/html/rfc7468#section-3
 *
 */
@RunWith(Parameterized.class)
public class DerDataTest {

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                 { "PRIVATE KEY", DerType.PRIVATE_KEY },
                 { "ENCRYPTED PRIVATE KEY", DerType.ENCRYPTED_PRIVATE_KEY },
                 { "PUBLIC KEY", DerType.PUBLIC_KEY },
           });
    }
    
    @Parameter(0)
    public String label;
    @Parameter(1)
    public DerType expectedType;
   
    @Test
    public void testFromLaxPem() throws InvalidKeyException {
        String pem = buildPem(false, "VGVzdA==");
        assertDerData(pem, "Test".getBytes(StandardCharsets.US_ASCII));
    }
    
    String buildPem(boolean withLinebreaks, String base64) {
        StringBuilder pemBuilder = new StringBuilder();
        pemBuilder.append("-----BEGIN ").append(label).append("----- ");
        if (withLinebreaks) {
            pemBuilder.append("\n");
        }
        pemBuilder.append(base64);
        if (withLinebreaks) {
            pemBuilder.append("\n");
        }
        pemBuilder.append("-----END ").append(label).append("----- ");
        return pemBuilder.toString();
    }
    
    void assertDerData(String pem, byte[] expectedData) throws InvalidKeyException {
        DerData data = DerData.parseFromPem(pem);
        Assert.assertEquals(expectedType, data.getType());
        Assert.assertArrayEquals(expectedData, data.getData());
    }
}
