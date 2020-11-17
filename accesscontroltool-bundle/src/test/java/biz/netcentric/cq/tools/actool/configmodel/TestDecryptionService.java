package biz.netcentric.cq.tools.actool.configmodel;

import biz.netcentric.cq.tools.actool.crypto.DecryptionService;

public final class TestDecryptionService implements DecryptionService {

    @Override
    public String decrypt(String text) {
        return text.substring(1, text.length()-1);
    }
}