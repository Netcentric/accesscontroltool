package biz.netcentric.cq.tools.actool.configmodel;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;

public final class SimpleAEMCryptoSupport extends AemCryptoSupport {

    @Override
    public String unprotect(String password) {
        return password.substring(1, password.length()-1);
    }
}