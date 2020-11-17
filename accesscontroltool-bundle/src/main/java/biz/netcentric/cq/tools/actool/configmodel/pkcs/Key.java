package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.crypto.DecryptionService;

/** Class encapsulating a private key together with either a full certificate or just the public key. */
public class Key {

    public static final Logger LOG = LoggerFactory.getLogger(Key.class);

    private final PublicKey publicKey;
    private final PrivateKey privateKey; // unencrypted
    private final X509Certificate certificate;

    public static Key createFromKeyPair(DecryptionService decryptionService, String pemPkcs8PrivateKey, String encryptedPrivateKeyPassword,
            String pemDerPublicKey, PrivateKeyDecryptor privateKeyDecryptor)
            throws IOException, GeneralSecurityException {
        return new Key(decryptionService, pemPkcs8PrivateKey, encryptedPrivateKeyPassword, pemDerPublicKey, null, privateKeyDecryptor);
    }

    public static Key createFromPrivateKeyAndCertificate(DecryptionService decryptionService, String pemPkcs8PrivateKey,
            String encryptedPrivateKeyPassword,
            String pemCertificate, PrivateKeyDecryptor privateKeyDecryptor)
            throws IOException, GeneralSecurityException {
        return new Key(decryptionService, pemPkcs8PrivateKey, encryptedPrivateKeyPassword, null, pemCertificate, privateKeyDecryptor);
    }

    private Key(DecryptionService decryptionService, String pemPkcs8PrivateKey, String encryptedPrivateKeyPassword, String pemDerPublicKey,
            String pemCertificate, PrivateKeyDecryptor privateKeyDecryptor)
            throws IOException, GeneralSecurityException {
        super();
        if (!StringUtils.isBlank(pemCertificate)) {
            try (ByteArrayInputStream input = new ByteArrayInputStream(pemCertificate.getBytes(StandardCharsets.US_ASCII))) {
                certificate = getCertificate(input);
                publicKey = null;
            }
        } else {
            if (!StringUtils.isBlank(pemDerPublicKey)) {
                DerData derData = DerData.parseFromPem(pemDerPublicKey);
                if (derData.getType() != DerType.PUBLIC_KEY) {
                    throw new InvalidKeyException("The given public key is of wrong type " + derData.getType());
                }
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(derData.getData());
                publicKey = getPublicKey(keySpec);
                certificate = null;
            } else {
                throw new InvalidKeyException("Either the public key or the certicate must not be blank!");
            }
        }
        if (StringUtils.isBlank(pemPkcs8PrivateKey)) {
            throw new InvalidKeyException("The private key must not be blank!");
        }

        pemPkcs8PrivateKey = decryptionService.decrypt(pemPkcs8PrivateKey);

        DerData derData = DerData.parseFromPem(pemPkcs8PrivateKey);
        switch(derData.getType()) {
            case ENCRYPTED_PRIVATE_KEY:
                String keyPassword = decryptionService.decrypt(encryptedPrivateKeyPassword);
                privateKey = privateKeyDecryptor.decrypt(keyPassword.toCharArray(), derData.getData());
                break;
            case PRIVATE_KEY:
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derData.getData());
                privateKey = getPrivateKey(keySpec);
                break;
            default:
                throw new InvalidKeyException("The private key has wrong format " + derData.getType());
        }
        // verify that the keys belong to each other
        if (!isMatchingKeyPair(getPublicKey(), privateKey)) {
            throw new InvalidKeyException("The public and private keys are not matching");
        }
    }

    public KeyPair getKeyPair() {
        return new KeyPair(publicKey, privateKey);
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public PublicKey getPublicKey() {
        if (certificate != null) {
            return certificate.getPublicKey();
        } else {
            return publicKey;
        }
    }

    static PublicKey getPublicKey(X509EncodedKeySpec keySpec) throws NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (InvalidKeySpecException ignore) {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            return keyFactory.generatePublic(keySpec);
        }
    }

    static PrivateKey getPrivateKey(PKCS8EncodedKeySpec keySpec) throws NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (InvalidKeySpecException ignore) {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            return keyFactory.generatePrivate(keySpec);
        }
    }

    static X509Certificate getCertificate(InputStream input) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(input);
    }


    @Override
    public String toString() {
        return "Key [privateKey=" + privateKey
                + ", publicKey=" + publicKey + ", certificate=" + certificate + "]";
    }

    private static boolean isMatchingKeyPair(PublicKey publicKey, PrivateKey privateKey) throws NoSuchAlgorithmException {
        if (publicKey instanceof RSAPublicKey) {
            return isMatchingRsaKeyPair((RSAPublicKey) publicKey, privateKey);
        } else if (publicKey instanceof DSAPublicKey) {
            return isMatchingDsaKeyPair((DSAPublicKey) publicKey, privateKey);
        } else {
            throw new IllegalArgumentException("Only public keys for RSA and DSA are supported but found: " + publicKey.getClass());
        }
    }

    private static boolean isMatchingRsaKeyPair(RSAPublicKey publicKey, PrivateKey privateKey) throws NoSuchAlgorithmException {
        byte[] data = "test".getBytes(StandardCharsets.US_ASCII);
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(data);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(encrypted);
            return Arrays.equals(data, decrypted);
        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            LOG.warn("RSA key pair does not match {}", e, e);
            return false;
        }
    }

    private static boolean isMatchingDsaKeyPair(DSAPublicKey publicKey, PrivateKey privateKey) throws NoSuchAlgorithmException {
        byte[] data = "test".getBytes(StandardCharsets.US_ASCII);
        try {
            Signature dsa = Signature.getInstance("SHA/DSA");
            // first create signature
            dsa.initSign(privateKey);
            dsa.update(data);
            byte[] sig = dsa.sign();
            // then verify signature
            dsa.initVerify(publicKey);
            dsa.update(data);
            return dsa.verify(sig);
        } catch (InvalidKeyException | SignatureException e) {
            LOG.warn("DSA key pair does not match {}", e, e);
            return false;
        }
    }

}
