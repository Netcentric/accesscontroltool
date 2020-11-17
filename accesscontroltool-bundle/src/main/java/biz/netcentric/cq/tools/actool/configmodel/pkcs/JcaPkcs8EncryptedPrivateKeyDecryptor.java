package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.osgi.service.component.annotations.Component;

@Component
public class JcaPkcs8EncryptedPrivateKeyDecryptor implements PrivateKeyDecryptor {

    @Override
    public PrivateKey decrypt(char[] password, byte[] derData) throws GeneralSecurityException, IOException {
        final PKCS8EncodedKeySpec keySpec;
        EncryptedPrivateKeyInfo encryptPKInfo;
        try {
            encryptPKInfo = new EncryptedPrivateKeyInfo(derData);
        } catch (java.io.IOException e) {
            if (e.getMessage().startsWith("ObjectIdentifier() -- data isn't an object ID ")) {
                // https://bugs.openjdk.java.net/browse/JDK-8076999
                throw new NoSuchAlgorithmException("Java < 11 does no properly support all PBES2 algorithms. Try again after installing Bouncycastle or upgrading to Java 11", e);
            } else {
                throw e;
            }
        }
        final Cipher cipher;
        try {
            cipher = Cipher.getInstance(encryptPKInfo.getAlgName());
        } catch (NoSuchAlgorithmException e) {
            // https://bugs.openjdk.java.net/browse/JDK-8231581
            throw new NoSuchAlgorithmException("JCA does no properly support all PBES2 algorithms. Try again after installing Bouncycastle", e);
        }
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
        SecretKeyFactory secFac = SecretKeyFactory.getInstance(encryptPKInfo.getAlgName());
        java.security.Key pbeKey = secFac.generateSecret(pbeKeySpec);
        AlgorithmParameters algParams = encryptPKInfo.getAlgParameters();
        cipher.init(Cipher.DECRYPT_MODE, pbeKey, algParams);
        keySpec = encryptPKInfo.getKeySpec(cipher);
        return Key.getPrivateKey(keySpec);
    }

}
