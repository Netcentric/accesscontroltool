package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test the different encodings in https://tools.ietf.org/html/rfc7468#section-3
 */
public class DerDataTest {

    public static Stream<Arguments> parameterProvider() {
        return Stream.of(
                arguments("PRIVATE KEY", DerType.PRIVATE_KEY),
                arguments("ENCRYPTED PRIVATE KEY", DerType.ENCRYPTED_PRIVATE_KEY),
                arguments("PUBLIC KEY", DerType.PUBLIC_KEY)
        );
    }

    @ParameterizedTest
    @MethodSource("parameterProvider")
    public void testFromLaxPem(final String label, final DerType expectedType) throws InvalidKeyException {
        String pem = buildPem(label, false, "VGVz   dA==");
        assertDerData(expectedType, pem, "Test".getBytes(StandardCharsets.US_ASCII));
    }

    String buildPem(final String label, boolean withLinebreaks, String base64) {
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

    void assertDerData(final DerType expectedType, String pem, byte[] expectedData) throws InvalidKeyException {
        DerData data = DerData.parseFromPem(pem);
        assertEquals(expectedType, data.getType());
        assertArrayEquals(expectedData, data.getData());
    }

    @Test
    public void testCert() throws InvalidKeyException, CertificateException {
        String pem = "-----BEGIN CERTIFICATE----- MIIDTjCCAjagAwIBAgIIYFkxjDzo/ZwwDQYJKoZIhvcNAQELBQAwZzELMAkGA1UEBhMCVVMxCzAJ BgNVBAgTAkNBMRYwFAYDVQQHEw1TYW4gRnJhbmNpc2NvMQ4wDAYDVQQKEwVBZG9iZTERMA8GA1UE CxMIU2VjdXJpdHkxEDAOBgNVBAMTB0NvbnNvbGUwHhcNMjAxMTEyMTMwNjIzWhcNMjExMTEyMTMw NjIzWjBnMQswCQYDVQQGEwJVUzELMAkGA1UECBMCQ0ExFjAUBgNVBAcTDVNhbiBGcmFuY2lzY28x DjAMBgNVBAoTBUFkb2JlMREwDwYDVQQLEwhTZWN1cml0eTEQMA4GA1UEAxMHQ29uc29sZTCCASIw DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAIEmzZRXJs8r5SHJmlnii2dvGwuVz5mnK9qqY0rm pVbyvVt4epzCi692CD5k8c0GXFS+1CHnUsGNiqB19psYdZDi3G6TfT82H0aoVcDC0fUQUCyTyPWh ug77EaF9tMK7wVvsTn/Cf8rez+4mj9isTwLEmM20EeJ9BuLDObeFb0HQ/O3Mp9GD2s9dxFsUAlm7 asYzOIUoBJ3jmLl0GqWL8X5rejOQRCOX/WdmfNkZjTPCUXtKqAA0eSWP4eaeZEvfNsVd3CAP/pKv Of+tHBmU4O8Paus9jsZqpF+Ah53qB7g1X4g95fG24GIeSdRTIZZidg/Vi2ghV0eqgQwZcLpFop0C AwEAATANBgkqhkiG9w0BAQsFAAOCAQEATDn36jD2Auw3n9tSVTntXkhRBOMgMftIMtjyXgvU3auX a03W4kbLWoTOyeySUW46XyS4YhsEGAjJfjmmRTOcSaRBn9Xid26XqMVAX8xc2KByxbqf7Xl2B66M 3cTLqUordnwZc7eG2q9TJ6zisCQjxRSZaV5+i/NtLcxENp24qr7mCVfLzEf3dRsRgF5nBftVHNwN pAn339a7+X71YciE7omS02ZHjOlhEYcge8SckEKk1Kyi9lZyefgN5efbLU00HJBLY02cFr5YagNq /S/+6Bkx+4srLkSXXlDxlGJ62OI/sUjcIUs94905jScm83kBkC5ough5C+Qljl7ae3dz6g== -----END CERTIFICATE-----";
        DerData data = DerData.parseFromPem(pem);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        cf.generateCertificate(new ByteArrayInputStream(data.getData()));
    }
}
