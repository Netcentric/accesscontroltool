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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.test.cq.CqTest;

public class AceServiceImplTest extends CqTest {

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
        session = getAdminSession();

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

    @Test
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
    public void tearDown() {
        this.session.logout();
    }

}
