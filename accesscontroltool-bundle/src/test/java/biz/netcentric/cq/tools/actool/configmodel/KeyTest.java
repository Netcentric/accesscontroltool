package biz.netcentric.cq.tools.actool.configmodel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCSException;
import org.junit.Ignore;
import org.junit.Test;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;

public class KeyTest {

    private final class SimpleAEMCryptoSupport implements AemCryptoSupport {

        @Override
        public String unprotect(String password) {
            return password.substring(1);
        }
    }

    @Test
    @Ignore
    public void testEncryptedPkcs8RsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test(expected=InvalidKeyException.class)
    @Ignore
    public void testEncryptedPkcs8RsaKeyWithUnrelatedCertificate() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test
    public void testEncryptedPkcs8Pbes2RsaKeyWithCertificate() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example5_rsa_pkcs8");
                InputStream inputPemCert = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemCert, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(privateKey, "{password", certificate);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testUnencryptedPkcs8RsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example2_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testOpenSsshRsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example3_rsa_openssh");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testInvalidPasswordWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(privateKey, "   ", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testInvalidPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example4_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(privateKey, "{password", publicKey);
            key.getKeyPair(new SimpleAEMCryptoSupport());
        }
    }

}
