package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;

public enum DerType {

    PUBLIC_KEY("Public Key", DerData.PUBLIC_KEY_PATTERN),
    PRIVATE_KEY("PKCS#8 Private Key", DerData.PRIVATE_KEY_PATTERN),
    ENCRYPTED_PRIVATE_KEY("PKCS#8 Encrypted Private Key", DerData.ENCRYPTED_PRIVATE_KEY_PATTERN),;
    
    private final String label;
    private final Pattern pattern;
    
    private DerType(String label, Pattern pattern) {
        this.label = label;
        this.pattern = pattern;
    }

    public Pattern getPattern() {
        return pattern;
    }
    
    byte[] fromPem(String pem) {
        Matcher matcher = pattern.matcher(pem);
        if (!matcher.find()) {
            return null;
        }
        return Base64.decodeBase64(matcher.group(1));
    }
}
