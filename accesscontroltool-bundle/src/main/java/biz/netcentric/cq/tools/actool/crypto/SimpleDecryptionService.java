package biz.netcentric.cq.tools.actool.crypto;

import org.osgi.service.component.annotations.Component;

/**
 * This service is only used as fallback in case {@link biz.netcentric.cq.tools.actool.aem.AemCryptoDecryptionService}
 * is not available.
 */
@Component
public class SimpleDecryptionService implements DecryptionService {

    @Override
    public String decrypt(String text) {
        if (!isEncrypted(text)) {
            return text;
        }
        throw new UnsupportedOperationException("Decrypting the encrypted value '" + text + "' requires the CryptoSupport service!");
    }

    /**
     * Copied from CryptoImpl, need it separately to be able to check if decryption is necessary before CryptoSupport is loaded
     */
    private static boolean isEncrypted(String text) {
        return text != null && text.length() > 2 && text.charAt(0) == '{' && text.charAt(text.length() - 1) == '}' && text.length() % 2 == 0 && text.lastIndexOf('{') == 0;
    }
}
