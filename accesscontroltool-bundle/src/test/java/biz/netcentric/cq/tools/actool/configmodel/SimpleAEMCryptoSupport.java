package biz.netcentric.cq.tools.actool.configmodel;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;

public final class SimpleAEMCryptoSupport implements AemCryptoSupport {

    @Override
    public String unprotect(String password) {
        return password.substring(1);
    }
    
    @Override
    public boolean isProtected(String password) {
        return password.startsWith("{");
    }
}