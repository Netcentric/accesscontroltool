package biz.netcentric.cq.tools.actool.installhook;

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.jcr.vault.fs.api.ProgressTrackerListener;
import com.day.jcr.vault.fs.io.ImportOptions;
import com.day.jcr.vault.packaging.InstallHook;

public abstract class OsgiAwareInstallHook implements InstallHook {

	private final BundleContext bundleContext;
	private static final Logger LOG = LoggerFactory.getLogger(OsgiAwareInstallHook.class);

	public OsgiAwareInstallHook() throws ClassCastException {
		// https://gist.github.com/tux2323/1314120
		bundleContext = BundleReference.class
				.cast(OsgiAwareInstallHook.class.getClassLoader()).getBundle()
				.getBundleContext();
	}

	public BundleContext getBundleContext() {
		return bundleContext;
	}

	public ServiceReference getServiceReference(String clazz) {
		ServiceReference serviceReference = bundleContext
				.getServiceReference(clazz);
		return serviceReference;
	}

	public void log (String message, ImportOptions options) {
		ProgressTrackerListener listener = options.getListener();
		if (listener != null) {
			listener.onMessage(ProgressTrackerListener.Mode.TEXT, message, "");
		} else {
			LOG.info(message);
		}
	}
}
