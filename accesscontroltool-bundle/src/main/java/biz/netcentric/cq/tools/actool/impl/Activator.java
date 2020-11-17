package biz.netcentric.cq.tools.actool.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aem.AemCryptoDecryptionService;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.BouncycastlePkcs8EncryptedPrivateKeyDecryptor;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.PrivateKeyDecryptor;
import biz.netcentric.cq.tools.actool.crypto.DecryptionService;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {

    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    @Override
    public void start(BundleContext context) throws Exception {
        // try to load Bouncy Castle
        try {
            BouncycastlePkcs8EncryptedPrivateKeyDecryptor privateKeyDecryptor = new BouncycastlePkcs8EncryptedPrivateKeyDecryptor();
            Dictionary<String, Object> properties = new Hashtable<>();
            properties.put(Constants.SERVICE_RANKING, Integer.valueOf(1000));
            context.registerService(PrivateKeyDecryptor.class, privateKeyDecryptor, properties);
        } catch (Throwable t) {
            LOG.info("Can not load Bouncycastle, probably not installed!");
            LOG.debug("Exception while loading Bouncy Castle", t);
        }
        
        // try to load Crypto Support
        try {
            AemCryptoDecryptionService decryptionService = new AemCryptoDecryptionService();
            Dictionary<String, Object> properties = new Hashtable<>();
            properties.put(Constants.SERVICE_RANKING, Integer.valueOf(1000));
            context.registerService(DecryptionService.class, decryptionService, properties);
        } catch (Throwable t) {
            LOG.info("Can not load AemCryptoDecryptionService, probably AEMs CryptoSupport is not running!");
            LOG.debug("Exception while loading AemCryptoDecryptionService", t);
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // TODO Auto-generated method stub

    }

}
