/*
 * (C) Copyright 2023 Cognizant Netcentric.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.extensions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.jcr.LoginException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.core.data.FileDataStore;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.blob.datastore.DataStoreBlobStore;
import org.apache.jackrabbit.oak.plugins.tree.impl.RootProviderService;
import org.apache.jackrabbit.oak.plugins.tree.impl.TreeProviderService;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.security.user.RandomAuthorizableNodeName;
import org.apache.jackrabbit.oak.segment.SegmentNodeStore;
import org.apache.jackrabbit.oak.segment.SegmentNodeStoreBuilders;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.apache.jackrabbit.oak.segment.file.FileStoreBuilder;
import org.apache.jackrabbit.oak.segment.file.InvalidFileStoreVersionException;
import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableNodeName;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.oak.spi.security.user.action.AccessControlAction;
import org.apache.jackrabbit.oak.spi.xml.ImportBehavior;
import org.apache.jackrabbit.oak.spi.xml.ProtectedItemImporter;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit5 extension for ITs leveraging an in-memory Oak repository.
 *
 */
public class OakRepository implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback, ParameterResolver {

    private static final Path DIR_OAK_REPO_HOME = Paths.get("target", "repository-oak-" + System.getProperty("repoSuffix", "fork1"));

    private static final Path DIR_OAK_FILE_STORE = DIR_OAK_REPO_HOME.resolve("filestore");
    private static final Path DIR_OAK_BLOB_STORE = DIR_OAK_FILE_STORE.resolve("blobstore");

    private static FileStore fileStore = null;

    private Repository repository;

    private Session admin;

    private static final Logger LOGGER = LoggerFactory.getLogger(OakRepository.class);

    /**
     * Optionally uses a dedicated BlobStore with Oak, otherwise just in memory
     */
    private final boolean useFileStore; 
    
    private final Consumer<Jcr> jcrInitCallback;

    public OakRepository() {
        this(true);
    }

    public OakRepository(boolean useFileStore) {
        this(useFileStore, null);
    }

    public OakRepository(boolean useFileStore, Consumer<Jcr> jcrInitCallback) {
        this.useFileStore = useFileStore;
        this.jcrInitCallback = jcrInitCallback;
    }
    
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        shutdownRepository();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        initRepository();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        admin = createAdminSession();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        admin.logout();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == Session.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return admin;
    }

    /** 
     * @param useFileStore only evaluated for Oak. O
     * @throws RepositoryException
     * @throws IOException
     * @throws InvalidFileStoreVersionException */
    private void initRepository() throws RepositoryException, IOException, InvalidFileStoreVersionException {
        Jcr jcr;
        if (useFileStore) {
            BlobStore blobStore = createBlobStore();
            Files.createDirectories(DIR_OAK_BLOB_STORE);
            fileStore = FileStoreBuilder.fileStoreBuilder(DIR_OAK_FILE_STORE.toFile())
                    .withBlobStore(blobStore)
                    .build();
            SegmentNodeStore nodeStore = SegmentNodeStoreBuilders.builder(fileStore).build();
            jcr = new Jcr(nodeStore);
        } else {
            // in-memory repo
            jcr = new Jcr();
        }
        if (jcrInitCallback != null) {
            jcrInitCallback.accept(jcr);
        }
        repository = jcr
                .with(createSecurityProvider())
                .withAtomicCounter()
                .createRepository();

        // setup default read ACL for everyone
        Session admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        AccessControlUtils.addAccessControlEntry(admin, "/", EveryonePrincipal.getInstance(), new String[] { "jcr:read" }, true);
        admin.save();
        admin.logout();

        LOGGER.info("repository created: {} {}",
                repository.getDescriptor(Repository.REP_NAME_DESC),
                repository.getDescriptor(Repository.REP_VERSION_DESC));
    }

    private void shutdownRepository() throws IOException {
        if (repository instanceof org.apache.jackrabbit.oak.jcr.repository.RepositoryImpl) {
            ((org.apache.jackrabbit.oak.jcr.repository.RepositoryImpl) repository).shutdown();
            if (fileStore != null) {
                fileStore.close();
                fileStore = null;
                deleteDirectory(DIR_OAK_REPO_HOME);
            }
        }
        repository = null;
    }

    private static void deleteDirectory(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder())
            .forEach(t -> {
                try {
                    Files.delete(t);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static BlobStore createBlobStore() throws IOException {
        Files.createDirectories(DIR_OAK_BLOB_STORE);
        FileDataStore fds = new FileDataStore();
        fds.setMinRecordLength(4092);
        fds.setPath(DIR_OAK_BLOB_STORE.toAbsolutePath().toString());
        fds.init(DIR_OAK_REPO_HOME.toAbsolutePath().toString());
        return new DataStoreBlobStore(fds);
    }

    public static SecurityProvider createSecurityProvider() {
        SecurityProvider securityProvider = SecurityProviderBuilder.newBuilder()
                .with(getSecurityConfigurationParameters())
                .withRootProvider(new RootProviderService())
                .withTreeProvider(new TreeProviderService()).build();
        return securityProvider;
    }

    public static ConfigurationParameters getSecurityConfigurationParameters() {
        Properties userProps = new Properties();
        AuthorizableNodeName nameGenerator = new RandomAuthorizableNodeName();

        userProps.put(UserConstants.PARAM_USER_PATH, "/home/users");
        userProps.put(UserConstants.PARAM_GROUP_PATH, "/home/groups");
        userProps.put(AccessControlAction.USER_PRIVILEGE_NAMES, new String[] {PrivilegeConstants.JCR_ALL});
        userProps.put(AccessControlAction.GROUP_PRIVILEGE_NAMES, new String[] {PrivilegeConstants.JCR_READ});
        userProps.put(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, ImportBehavior.NAME_BESTEFFORT);
        userProps.put(UserConstants.PARAM_AUTHORIZABLE_NODE_NAME, nameGenerator);
        userProps.put("cacheExpiration", 3600*1000);
        Properties authzProps = new Properties();
        authzProps.put(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, ImportBehavior.NAME_BESTEFFORT);
        return ConfigurationParameters.of(
                UserConfiguration.NAME, ConfigurationParameters.of(userProps),
                AuthorizationConfiguration.NAME, ConfigurationParameters.of(authzProps));
    }

    public Session createAdminSession() throws RepositoryException {
        return repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
    }
}
