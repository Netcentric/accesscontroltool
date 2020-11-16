package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

@Component(property = Constants.SERVICE_RANKING +":Integer=1000")
public class BouncycastlePkcs8EncryptedPrivateKeyDecryptor implements PrivateKeyDecryptor {

    @Override
    public PrivateKey decrypt(char[] password, byte[] derData) throws GeneralSecurityException, IOException {
        // use BouncyCastle due to https://bugs.openjdk.java.net/browse/JDK-8231581
        JceOpenSSLPKCS8DecryptorProviderBuilder jce = new JceOpenSSLPKCS8DecryptorProviderBuilder();
        jce.setProvider(new BouncyCastleProvider());
        InputDecryptorProvider decProv;
        try {
            decProv = jce.build(password);
            PKCS8EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new PKCS8EncryptedPrivateKeyInfo(derData);
            PrivateKeyInfo info = encryptedPrivateKeyInfo.decryptPrivateKeyInfo(decProv);
            return new JcaPEMKeyConverter().getPrivateKey(info);
        } catch (OperatorCreationException | PKCSException e) {
            throw new InvalidKeyException("Invalid encrypted private key", e);
        }
    }

}
