package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.EncryptedPrivateKeyInfo;

public interface PrivateKeyDecryptor {

    PrivateKey decrypt(char[] password, byte[] derData) throws GeneralSecurityException, IOException;
}
