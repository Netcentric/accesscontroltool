package biz.netcentric.cq.tools.actool.aceservice;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;
import javax.naming.NamingException;

import org.apache.sling.commons.testing.jcr.RepositoryProvider;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public class AceServiceImplTest {

    Session session = null;
    String configurationRootPath = "/var/actool";

    Node varNode = null;
    Node acToolNode = null;

    Node project1Node = null;
    Node project2Node = null;
    Node project3Node = null;

    @Before
    public void setup() throws AccessDeniedException, ItemExistsException,
            ReferentialIntegrityException, ConstraintViolationException,
            InvalidItemStateException, VersionException, LockException,
            NoSuchNodeTypeException, RepositoryException {
        SlingRepository repo = RepositoryProvider.instance().getRepository();
        session = repo.login();
        Node rootNode = session.getRootNode();
        varNode = rootNode.addNode("var", "nt:folder");
        acToolNode = varNode.addNode("actool", "nt:folder");

        project1Node = setUpProjectNode(acToolNode, "project1");
        project2Node = setUpProjectNode(acToolNode, "project2");
        project3Node = setUpProjectNode(acToolNode, "project3");

    }

    private Node setUpProjectNode(Node acToolNode, String projectNodeName)
            throws ItemExistsException, PathNotFoundException,
            NoSuchNodeTypeException, LockException, VersionException,
            ConstraintViolationException, RepositoryException {
        Node node = acToolNode.addNode(projectNodeName, "nt:folder");
        node.addNode(projectNodeName + "_0.txt", "nt:file")
                .addNode("jcr:content", "nt:resource")
                .setProperty("jcr:data", ".");
        ;
        this.session.save();
        node.addNode(projectNodeName + "_1.txt", "nt:file")
                .addNode("jcr:content", "nt:resource")
                .setProperty("jcr:data", ".");
        ;
        this.session.save();
        node.addNode(projectNodeName + "_2.txt", "nt:file")
                .addNode("jcr:content", "nt:resource")
                .setProperty("jcr:data", ".");
        this.session.save();
        return node;
    }

    @Ignore // Fails with an error like 
    // java.io.FileNotFoundException: ...accesscontroltool-bundle/target/repository/workspaces/default/db/seg0/c20.dat (Too many open files in system)
    public void getNewestConfiguationNodesTest() throws Exception {
        AceService aceService = new AceServiceImpl();
        Map<String, String> configNodes = aceService
                .getNewestConfigurationNodes(configurationRootPath, session,
                        new AcInstallationHistoryPojo());
        Set<String> paths = configNodes.keySet();

        Set<String> expectedSet = new HashSet<String>();
        expectedSet.add("/var/actool/project1/project1_2.txt");
        expectedSet.add("/var/actool/project2/project2_2.txt");
        expectedSet.add("/var/actool/project3/project3_2.txt");
        assertTrue(expectedSet.containsAll(paths));
    }

    @After
    public void tearDown() throws NamingException {
        this.session.logout();
        RepositoryUtil.stopRepository();
    }

}
