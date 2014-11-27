package biz.netcentric.cq.tools.actool.authorizableutils;

import java.util.LinkedHashSet;
import java.util.Map;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;

import org.apache.sling.jcr.api.SlingRepository;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface AuthorizableCreatorService {

    public void createNewAuthorizables(
            Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMapFromConfig,
            final Session session, AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException,
            AuthorizableCreatorException;

    public void performRollback(SlingRepository repository,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            AcInstallationHistoryPojo history) throws RepositoryException;
}
