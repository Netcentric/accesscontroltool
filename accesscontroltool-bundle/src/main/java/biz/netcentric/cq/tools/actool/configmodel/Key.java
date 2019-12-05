package biz.netcentric.cq.tools.actool.configmodel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;

/**
 * Class encapsulating a private key together with either a full certificate or just the public key.
 */
public class Key {

    private final PKCS8EncryptedPrivateKeyInfo encryptedPrivateKeyInfo;
    private final String encryptedPrivateKeyPassword;

    private final PublicKey publicKey;
    private final X509Certificate certificate;

    // as defined in https://tools.ietf.org/html/rfc7468#section-13
    private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile(
            "-+BEGIN PUBLIC KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" + // Base64 text
                    "-+END PUBLIC KEY[^-]*-+", // Footer
            Pattern.CASE_INSENSITIVE);

    public static Key createFromKeyPair(String pemPkcs8PrivateKey, String encryptedPrivateKeyPassword, String pemDerPublicKey)
            throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException, CertificateException, IOException {
        return new Key(pemPkcs8PrivateKey, encryptedPrivateKeyPassword, pemDerPublicKey, null);
    }

    public static Key createFromPrivateKeyAndCertificate(String pemPkcs8PrivateKey, String encryptedPrivateKeyPassword,
            String pemCertificate)
            throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException, CertificateException, IOException {
        return new Key(pemPkcs8PrivateKey, encryptedPrivateKeyPassword, null, pemCertificate);
    }

    private Key(String pemPkcs8PrivateKey, String encryptedPrivateKeyPassword, String pemDerPublicKey, String pemCertificate)
            throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException, CertificateException, IOException {
        super();
        if (StringUtils.isBlank(pemPkcs8PrivateKey)) {
            throw new InvalidKeyException("The private key must not be blank!");
        }
        
        try {
            this.encryptedPrivateKeyInfo = decodePKCS8PEM(pemPkcs8PrivateKey);
        } catch (IOException e) {
            throw new InvalidKeyException("The private key format is wrong", e);
        }
        if (encryptedPrivateKeyInfo == null) {
            throw new InvalidKeyException("The private key is empty");
        }
        // the same as CryptoSupport.isProtected(...) but must work without the dependency
        if (!encryptedPrivateKeyPassword.startsWith("{")) {
            throw new InvalidKeyException("The private key password must be given as encrypted value (encrypted with the AEM Crypto Support, i.e. start with '{')");
        }
        this.encryptedPrivateKeyPassword = encryptedPrivateKeyPassword;

        if (!StringUtils.isBlank(pemCertificate)) {
            try (ByteArrayInputStream input = new ByteArrayInputStream(pemCertificate.getBytes(StandardCharsets.US_ASCII))) {
                certificate = getCertificate(input);
                publicKey = null;
            }
        } else {
            if (!StringUtils.isBlank(pemDerPublicKey)) {
                byte[] derPublicKey = decodePemDer(pemDerPublicKey);
                publicKey = getPublicKey(derPublicKey);
                certificate = null;
            } else {
                throw new InvalidKeyException("Either the public key or the certicate must not be blank!");
            }
        }
    }

    public PrivateKey getPrivateKey(AemCryptoSupport cryptoSupport) throws IOException, GeneralSecurityException {
        if (cryptoSupport == null) {
            throw new IllegalArgumentException("CryptoSupport has not been provided but it is required to deal with PKCS#8 keys.");
        }
        String keyPassword = cryptoSupport.unprotect(encryptedPrivateKeyPassword);
        PrivateKey privateKey;
        try {
            privateKey = getPrivateKey(keyPassword);
        } catch (OperatorCreationException | PKCSException e) {
            throw new GeneralSecurityException("Could not decrypt private key", e);
        }
        // verify that the keys belong to each other
        if (!isMatchingKeyPair(getPublicKey(), privateKey)) {
            throw new InvalidKeyException("The public and private keys are not matching");
        }
        return privateKey;
    }

    public KeyPair getKeyPair(AemCryptoSupport cryptoSupport) throws IOException, GeneralSecurityException {
        PrivateKey privateKey = getPrivateKey(cryptoSupport);
        return new KeyPair(publicKey, privateKey);
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

    static PublicKey getPublicKey(byte[] derPublicKey) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(derPublicKey);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (InvalidKeySpecException ignore) {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            return keyFactory.generatePublic(spec);
        }
    }

    static X509Certificate getCertificate(InputStream input) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(input);
        return cert;
    }

    private PrivateKey getPrivateKey(String keyPassword) throws IOException, GeneralSecurityException, PKCSException, OperatorCreationException {
        JceOpenSSLPKCS8DecryptorProviderBuilder jce = new JceOpenSSLPKCS8DecryptorProviderBuilder();
        jce.setProvider(new BouncyCastleProvider());
        InputDecryptorProvider decProv = jce.build(keyPassword.toCharArray());
        PrivateKeyInfo info = encryptedPrivateKeyInfo.decryptPrivateKeyInfo(decProv);
        return new JcaPEMKeyConverter().getPrivateKey(info);
    }


    /** <a href="https://tools.ietf.org/html/rfc7468#section-11">RFC 7468</a>
     * 
     * @throws IOException */
    static PKCS8EncryptedPrivateKeyInfo decodePKCS8PEM(String pemPrivateKey) throws IOException {
        try (PEMParser parser = new PEMParser(new StringReader(pemPrivateKey))) {
            Object object = parser.readObject();
            if (!(object instanceof PKCS8EncryptedPrivateKeyInfo)) {
                throw new IOException("Invalid pem object, must be an encrypted private key but is a " + object);
            }
            return (PKCS8EncryptedPrivateKeyInfo)object;
        }
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
        return "Key [encryptedPrivateKeyInfo=" + encryptedPrivateKeyInfo.toString() + ", encryptedPrivateKeyPassword=" + encryptedPrivateKeyPassword
                + ", publicKey=" + publicKey + ", certificate=" + certificate + "]";
    }

    private static boolean isMatchingKeyPair(PublicKey publicKey, PrivateKey privateKey) throws NoSuchAlgorithmException, NoSuchPaddingException {
        String data = "text";
        Cipher cipher;
        cipher = createCipher(publicKey);
        try {
            byte[] encrypted = encrypt(data, publicKey, cipher);
            String decrypted = decrypt(encrypted, privateKey, cipher);
            return data.equals(decrypted);
        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Cipher createCipher(PublicKey publicKey) throws NoSuchAlgorithmException, NoSuchPaddingException {
        final Cipher cipher;
        if (publicKey instanceof RSAPublicKey) {
            cipher = Cipher.getInstance("RSA");
        } else if (publicKey instanceof DSAPublicKey) {
            cipher = Cipher.getInstance("DSA");
        } else {
            throw new IllegalArgumentException("Only public keys for RSA and DSA are supported but found: " + publicKey.getClass());
        }
        return cipher;
    }

    private static byte[] encrypt(String data, PublicKey publicKey, Cipher cipher) throws BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data.getBytes(StandardCharsets.US_ASCII));
    }

    private static String decrypt(byte[] data, PrivateKey privateKey, Cipher cipher) throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(cipher.doFinal(data), StandardCharsets.US_ASCII);
    }
}
