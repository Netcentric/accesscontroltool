package biz.netcentric.cq.tools.actool.configmodel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCSException;
import org.junit.Before;
import org.junit.Test;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;

public class KeyTest {

    private AemCryptoSupport cryptoSupport;

    @Before
    public void setUp() {
        cryptoSupport = new SimpleAEMCryptoSupport();
    }

    @Test
    public void testEncryptedPkcs8RsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(cryptoSupport, privateKey, "{password", publicKey);
            key.getKeyPair();
            key.getPrivateKey();
        }
    }

    @Test
    public void testEncryptedPkcs8DsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_dsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_dsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(cryptoSupport, privateKey, "{password", publicKey);
            key.getKeyPair();
            key.getPrivateKey();
        }
    }

    @Test(expected=InvalidKeyException.class)
    public void testEncryptedPkcs8RsaKeyWithUnrelatedCertificate() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromPrivateKeyAndCertificate(cryptoSupport, privateKey, "{password", publicKey);
        }
    }

    @Test
    public void testEncryptedPkcs8Pbes2RsaKeyWithCertificate() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example5_rsa_pkcs8");
                InputStream inputPemCert = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemCert, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(cryptoSupport, privateKey, "{password", certificate);
            key.getKeyPair();
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testPkcs1RsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example2_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromKeyPair(cryptoSupport, privateKey, "{password", publicKey);
        }
    }

    @Test(expected = IOException.class)
    public void testOpenSshRsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example3_rsa_openssh");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromKeyPair(cryptoSupport, privateKey, "{password", publicKey);
        }
    }

    @Test(expected = GeneralSecurityException.class)
    public void testInvalidPasswordWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromKeyPair(cryptoSupport, privateKey, "   ", publicKey);
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testInvalidPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example4_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromKeyPair(cryptoSupport, privateKey, "{password", publicKey);
        }
    }

    @Test
    public void testUnencryptedPkcs8RsaKeyWithCert() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example6_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example6_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(cryptoSupport, privateKey, "", certificate);
            key.getKeyPair();
            key.getPrivateKey();
        }
    }
}
