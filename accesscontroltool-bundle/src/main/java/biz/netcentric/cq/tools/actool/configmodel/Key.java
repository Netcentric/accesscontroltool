package biz.netcentric.cq.tools.actool.configmodel;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.adobe.granite.crypto.CryptoSupport;

public class Key {

    private PrivateKey key; // the PKCS#8 encrypted key

    public PrivateKey getKey() {
        return key;
    }

    public void setKey(String pemPrivateKey, String encryptedPassword, CryptoSupport cryptoSupport) {
        if (cryptoSupport == null) {
            throw new IllegalArgumentException("CryptoSupport has not been provided but it is required to deal with PKCS#8 keys.");
        }
        String password = cryptoSupport.unprotect(encryptedPassword);
        
        // PEM to DER encoding
        
        byte[] pkcs8Data;
        // 
        PBEKeySpec pbeSpec = new PBEKeySpec(password.toCharArray());
        SecretKeyFactory skf = SecretKeyFactory.getInstance(pkinfo.getAlgName());
        SecretKey secret = skf.generateSecret(pbeSpec);
        EncryptedPrivateKeyInfo pkinfo = new EncryptedPrivateKeyInfo(pkcs8Data);
        PKCS8EncodedKeySpec keySpec = pkinfo.getKeySpec(secret);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        this.key = kf.generatePrivate(keySpec);
    }

}
