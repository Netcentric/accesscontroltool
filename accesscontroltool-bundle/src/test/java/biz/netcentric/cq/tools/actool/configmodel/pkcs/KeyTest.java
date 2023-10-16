package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCSException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import biz.netcentric.cq.tools.actool.configmodel.TestDecryptionService;
import biz.netcentric.cq.tools.actool.crypto.DecryptionService;

public class KeyTest {

    private DecryptionService descryptionService;
    private PrivateKeyDecryptor privateKeyDecryptor;

    @BeforeEach
    public void setUp() {
        descryptionService = new TestDecryptionService();
        privateKeyDecryptor = new JcaPkcs8EncryptedPrivateKeyDecryptor();
    }

    @Test
    public void testEncryptedPkcs8RsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
             InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromKeyPair(descryptionService, privateKey, "{password}", publicKey, privateKeyDecryptor);
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
            Key key = Key.createFromKeyPair(descryptionService, privateKey, "{password}", publicKey, privateKeyDecryptor);
            key.getKeyPair();
            key.getPrivateKey();
        }
    }

    @Test
    public void testEncryptedPkcs8RsaKeyWithUnrelatedCertificate() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
             InputStream inputPemDer = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            assertThrows(InvalidKeyException.class,
                    () -> Key.createFromPrivateKeyAndCertificate(descryptionService, privateKey, "{password}", publicKey, privateKeyDecryptor));
        }
    }

    @Test
    @EnabledForJreRange(max = JRE.JAVA_17)
    // https://bugs.openjdk.java.net/browse/JDK-8231581 (Java 11) or https://bugs.openjdk.java.net/browse/JDK-8076999 (Java 8) but works with Java 21 (https://bugs.openjdk.org/browse/JDK-8288050)
    public void testEncryptedPkcs8Pbes2RsaKeyWithCertificateOnJCASDefaultPriorJava21() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example5_rsa_pkcs8");
             InputStream inputPemCert = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemCert, StandardCharsets.US_ASCII);
            assertThrows(NoSuchAlgorithmException.class,
                    () -> Key.createFromPrivateKeyAndCertificate(descryptionService, privateKey, "{password}", certificate, privateKeyDecryptor));
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testEncryptedPkcs8Pbes2RsaKeyWithCertificateOnJCASDefaultJava21() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example5_rsa_pkcs8");
             InputStream inputPemCert = this.getClass().getResourceAsStream("example5_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemCert, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(descryptionService, privateKey, "{password}", certificate, privateKeyDecryptor);
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
            Key key = Key.createFromPrivateKeyAndCertificate(descryptionService, privateKey, "{password}", certificate, privateKeyDecryptor);
            key.getKeyPair();
        }
    }

    @Test
    public void testPkcs1RsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example2_rsa_pkcs8");
             InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            assertThrows(InvalidKeyException.class,
                    () -> Key.createFromKeyPair(descryptionService, privateKey, "{password}", publicKey, privateKeyDecryptor));
        }
    }

    @Test
    public void testOpenSshRsaKeyWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example3_rsa_openssh");
             InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            assertThrows(InvalidKeyException.class,
                    () -> Key.createFromKeyPair(descryptionService, privateKey, "{password}", publicKey, privateKeyDecryptor));
        }
    }

    @Test
    public void testInvalidPasswordWithPublicKey() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
             InputStream inputPemDer = this.getClass().getResourceAsStream("example1_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            assertThrows(GeneralSecurityException.class,
                    () -> Key.createFromKeyPair(descryptionService, privateKey, "   ", publicKey, privateKeyDecryptor));
        }
    }

    @Test
    public void testInvalidPublicKey() throws IOException, GeneralSecurityException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example1_rsa_pkcs8");
             InputStream inputPemDer = this.getClass().getResourceAsStream("example4_rsa_pub")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String publicKey = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            assertThrows(InvalidKeyException.class,
                    () -> Key.createFromKeyPair(descryptionService, privateKey, "{password}", publicKey, privateKeyDecryptor));
        }
    }

    @Test
    public void testUnencryptedPkcs8RsaKeyWithCert() throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException {
        try (InputStream inputPkcs8 = this.getClass().getResourceAsStream("example6_rsa_pkcs8");
             InputStream inputPemDer = this.getClass().getResourceAsStream("example6_rsa.crt")) {
            String privateKey = IOUtils.toString(inputPkcs8, StandardCharsets.US_ASCII);
            String certificate = IOUtils.toString(inputPemDer, StandardCharsets.US_ASCII);
            Key key = Key.createFromPrivateKeyAndCertificate(descryptionService, privateKey, "", certificate, privateKeyDecryptor);
            key.getKeyPair();
            key.getPrivateKey();
        }
    }
}
