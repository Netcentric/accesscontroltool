package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCSException;
import org.junit.Before;
import org.junit.Test;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;
import biz.netcentric.cq.tools.actool.configmodel.SimpleAEMCryptoSupport;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.BouncycastlePkcs8EncryptedPrivateKeyDecryptor;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.JcaPkcs8EncryptedPrivateKeyDecryptor;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.Key;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.PrivateKeyDecryptor;

public class KeyTest {

    private AemCryptoSupport cryptoSupport;
    private PrivateKeyDecryptor privateKeyDecryptor;

    @Before
    public void setUp() {
        cryptoSupport = new SimpleAEMCryptoSupport();
        privateKeyDecryptor = new JcaPkcs8EncryptedPrivateKeyDecryptor();
    }

    @Test
    public void testEncryptedPkcs8RsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(cryptoSupport, privateKey, "{password}", publicKey, privateKeyDecryptor);
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
            Key key = Key.createFromKeyPair(cryptoSupport, privateKey, "{password}", publicKey, privateKeyDecryptor);
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
            Key.createFromPrivateKeyAndCertificate(cryptoSupport, privateKey, "{password}", publicKey, privateKeyDecryptor);
        }
    }

    @Test(expected = NoSuchAlgorithmException.class)
    // https://bugs.openjdk.java.net/browse/JDK-8231581 (Java 11) or https://bugs.openjdk.java.net/browse/JDK-8076999 (Java 8)
    public void testEncryptedPkcs8Pbes2RsaKeyWithCertificateOnJCASDefault() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example5_rsa_pkcs8");
                InputStream inputPemCert = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemCert, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(cryptoSupport, privateKey, "{password}", certificate, privateKeyDecryptor);
            key.getKeyPair();
        }
    }

    @Test
    public void testEncryptedPkcs8Pbes2RsaKeyWithCertificateAndBouncycastle() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        privateKeyDecryptor = new BouncycastlePkcs8EncryptedPrivateKeyDecryptor();
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example5_rsa_pkcs8");
                InputStream inputPemCert = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemCert, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(cryptoSupport, privateKey, "{password}", certificate, privateKeyDecryptor);
            key.getKeyPair();
        }
    }
    
    @Test(expected = InvalidKeyException.class)
    public void testPkcs1RsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example2_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromKeyPair(cryptoSupport, privateKey, "{password}", publicKey, privateKeyDecryptor);
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testOpenSshRsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example3_rsa_openssh");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromKeyPair(cryptoSupport, privateKey, "{password}", publicKey, privateKeyDecryptor);
        }
    }

    @Test(expected = GeneralSecurityException.class)
    public void testInvalidPasswordWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromKeyPair(cryptoSupport, privateKey, "   ", publicKey, privateKeyDecryptor);
        }
    }

    @Test(expected = InvalidKeyException.class)
    public void testInvalidPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example4_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key.createFromKeyPair(cryptoSupport, privateKey, "{password}", publicKey, privateKeyDecryptor);
        }
    }

    @Test
    public void testUnencryptedPkcs8RsaKeyWithCert() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example6_rsa_pkcs8");
                InputStream inputPemDer = this.getClass().getResourceAsStream("example6_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(cryptoSupport, privateKey, "", certificate, privateKeyDecryptor);
            key.getKeyPair();
            key.getPrivateKey();
        }
    }
}
