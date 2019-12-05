package biz.netcentric.cq.tools.actool.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;


/**
 * URL Handler for scheme used by the JCR Installer (used e.g. in bundle location URLs).
 * Returns the original file contained in the underlying repository.
 * TODO: remove once https://issues.apache.org/jira/browse/SLING-8877 is implemented on all supported AEM versions
 * 
 * @see <a href="https://osgi.org/specification/osgi.core/7.0.0/service.url.html#d0e42987">OSGi URL Handlers</a>
 */
@Component(property = URLConstants.URL_HANDLER_PROTOCOL+"=jcrinstall")
public class JcrInstallUrlHandler extends AbstractURLStreamHandlerService implements URLStreamHandlerService {

    @Reference
    private ResourceResolverFactory resolverFactory;

    private ResourceResolver resolver;

    @Activate
    public void activate() throws LoginException {
        resolver = resolverFactory.getServiceResourceResolver(null);
    }
    
    @Deactivate
    public void deactivate() {
        resolver.close();
    }

    @Override
    public URLConnection openConnection(URL url) throws IOException {
        return new JcrInstallConnection(url, resolver);
    }

    private static final class JcrInstallConnection extends URLConnection {

        private final ResourceResolver resolver;
        private final String path;
        
        protected JcrInstallConnection(URL url, ResourceResolver resolver) {
            super(url);
            this.resolver = resolver;
            this.path = url.getPath();
        }

        @Override
        public void connect() throws IOException {
        }

        @Override
        public InputStream getInputStream() throws IOException {
            Resource resource = resolver.getResource(path);
            if (resource == null) {
                throw new IOException("Could not find resource at path '" + path + "'");
            }
            InputStream is = resource.adaptTo(InputStream.class);
            if (is == null) {
                throw new IOException("There is no binary resource at path '" + path + "'");
            }
            return is;
        }
    }

}
