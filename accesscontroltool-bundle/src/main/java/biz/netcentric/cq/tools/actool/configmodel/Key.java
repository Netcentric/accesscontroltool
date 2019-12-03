package biz.netcentric.cq.tools.actool.configmodel;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;

public class Key {

    private final byte[] pkcs8PrivateKey;
    private final byte[] derPublicKey;
    private final String encryptedPrivateKeyPassword;
    
    // as defined in https://tools.ietf.org/html/rfc7468#section-13
    private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile(
            "-+BEGIN PUBLIC KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" + // Base64 text
                    "-+END PUBLIC KEY[^-]*-+", // Footer
            Pattern.CASE_INSENSITIVE);

    
    // as defined in https://tools.ietf.org/html/rfc7468#section-11
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-+BEGIN ENCRYPTED PRIVATE KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" + // Base64 text
                    "-+END ENCRYPTED PRIVATE KEY[^-]*-+", // Footer
            Pattern.CASE_INSENSITIVE);

    
    
    public Key(String pemPkcs8PrivateKey, String encryptedPrivateKeyPassword, String pemDerPublicKey) throws InvalidKeyException {
        super();
        if (StringUtils.isBlank(pemPkcs8PrivateKey)) {
            throw new InvalidKeyException("The private key must not be blank!");
        }
        this.pkcs8PrivateKey = decodePKCS8PEM(pemPkcs8PrivateKey);
        // the same as CryptoSupport.isProtected(...) but must work without the dependency
        if (!encryptedPrivateKeyPassword.startsWith("{")) {
            throw new InvalidKeyException("The private key password must be given as encrypted value (i.e. start with '{'");
        }
        this.encryptedPrivateKeyPassword = encryptedPrivateKeyPassword;
        if (StringUtils.isBlank(pemDerPublicKey)) {
            throw new InvalidKeyException("The public key must not be blank!");
        }
        this.derPublicKey = decodePemDer(pemDerPublicKey);
    }

    public KeyPair getKeyPair(AemCryptoSupport cryptoSupport) throws IOException, GeneralSecurityException {
        if (cryptoSupport == null) {
            throw new IllegalArgumentException("CryptoSupport has not been provided but it is required to deal with PKCS#8 keys.");
        }
        String keyPassword = cryptoSupport.unprotect(encryptedPrivateKeyPassword);
        return getKeyPair(keyPassword);
    }

    KeyPair getKeyPair(String privateKeyPassword) throws IOException, GeneralSecurityException {
        PublicKey publicKey = getPublicKey();
        PrivateKey privateKey = getPrivateKey(privateKeyPassword);
        return new KeyPair(publicKey, privateKey);
    }

    PublicKey getPublicKey() throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(derPublicKey);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (InvalidKeySpecException ignore) {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            return keyFactory.generatePublic(spec);
        }
    }
 
    PrivateKey getPrivateKey(String keyPassword) throws IOException, GeneralSecurityException {
        PKCS8EncodedKeySpec encodedKeySpec = readPrivateKeySpec(keyPassword);
        // as reading out the algorithm from the pkcs#8 data requires bouncycastle just try out both supported algorithms
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(encodedKeySpec);
        } catch (InvalidKeySpecException ignore) {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            return keyFactory.generatePrivate(encodedKeySpec);
        }
    }

    PKCS8EncodedKeySpec readPrivateKeySpec(String keyPassword) throws IOException, GeneralSecurityException {
        EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(pkcs8PrivateKey);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
        SecretKey secretKey = keyFactory.generateSecret(new PBEKeySpec(keyPassword.toCharArray()));

        Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, secretKey, encryptedPrivateKeyInfo.getAlgParameters());

        return encryptedPrivateKeyInfo.getKeySpec(cipher);
    }

    /** <a href="https://tools.ietf.org/html/rfc7468#section-11">RFC 7468</a>
     * 
     * @throws InvalidKeyException */
    static byte[] decodePKCS8PEM(String pemPrivateKey) throws InvalidKeyException {
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(pemPrivateKey);
        if (!matcher.find()) {
            throw new InvalidKeyException(
                    "Private key has not been given in the expected encrypted PKCS#8 PEM format as defined in https://tools.ietf.org/html/rfc7468#section-11");
        }
        return base64Decode(matcher.group(1));
    }

    /** <a href="https://tools.ietf.org/html/rfc7468#section-5.1">RFC 7468</a>
     * 
     * @throws InvalidKeyException */
    static byte[] decodePemDer(String pemDer) throws InvalidKeyException {
        Matcher matcher = PUBLIC_KEY_PATTERN.matcher(pemDer);
        if (!matcher.find()) {
            throw new InvalidKeyException(
                    "Public key has not been given in the expected PEM DER format as defined in https://tools.ietf.org/html/rfc7468#section-13");
        }
        return base64Decode(matcher.group(1));
    }

    static byte[] base64Decode(String base64) {
        return Base64.decodeBase64(base64);
    }

    @Override
    public String toString() {
        return "Key [pkcs8PrivateKey=" + Arrays.toString(pkcs8PrivateKey) + ", derPublicKey=" + Arrays.toString(derPublicKey)
                + ", encryptedPrivateKeyPassword=" + encryptedPrivateKeyPassword + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(derPublicKey);
        result = prime * result + ((encryptedPrivateKeyPassword == null) ? 0 : encryptedPrivateKeyPassword.hashCode());
        result = prime * result + Arrays.hashCode(pkcs8PrivateKey);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Key other = (Key) obj;
        if (!Arrays.equals(derPublicKey, other.derPublicKey))
            return false;
        if (encryptedPrivateKeyPassword == null) {
            if (other.encryptedPrivateKeyPassword != null)
                return false;
        } else if (!encryptedPrivateKeyPassword.equals(other.encryptedPrivateKeyPassword))
            return false;
        if (!Arrays.equals(pkcs8PrivateKey, other.pkcs8PrivateKey))
            return false;
        return true;
    }
}
