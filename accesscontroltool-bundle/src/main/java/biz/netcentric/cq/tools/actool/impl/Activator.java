package biz.netcentric.cq.tools.actool.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.pkcs.BouncycastlePkcs8EncryptedPrivateKeyDecryptor;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.PrivateKeyDecryptor;

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
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // Nothing to do as services are automatically unregistered when bundle stops
    }

}
