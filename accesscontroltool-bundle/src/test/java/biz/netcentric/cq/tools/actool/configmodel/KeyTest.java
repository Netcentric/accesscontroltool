package biz.netcentric.cq.tools.actool.configmodel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;

public class KeyTest {

    private final class SimpleAEMCryptoSupport implements AemCryptoSupport {

        @Override
        public String unprotect(String password) {
            return password.substring(1);
        }
    }

    @Before
    public void setUp() {
    }

    @Test
    public void testEncryptedPkcs8RsaKey() throws IOException, GeneralSecurityException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = new Key(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testUnencryptedPkcs8RsaKey() throws IOException, GeneralSecurityException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example2_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = new Key(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }
    
    @Test(expected = InvalidKeyException.class)
    public void testOpenSsshRsaKey() throws IOException, GeneralSecurityException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example3_rsa_openssh");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = new Key(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testInvalidPassword() throws IOException, GeneralSecurityException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = new Key(privateKey, "   ", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testInvalidPublicKey() throws IOException, GeneralSecurityException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example4_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = new Key(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }
}
