package biz.netcentric.cq.tools.actool.validators;


import static biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMergerTest.getAcConfigurationForFile;
import static biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMergerTest.getConfigurationMerger;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.jcr.Session;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class UnmangedExternalMemberRelationshipCheckerTest {

    @Mock
    Session session;

    @Test
    public void testInvalidIsMemberOf1() {
        assertThrows(IllegalArgumentException.class,
                () -> getAcConfigurationForFile(getConfigurationMerger(), session, "invalid-unmanaged/test-invalid-unmanged-isMemberOf1.yaml"));
    }
    
    @Test
    public void testInvalidIsMemberOf2() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> getAcConfigurationForFile(getConfigurationMerger(), session, "invalid-unmanaged/test-invalid-unmanged-isMemberOf2.yaml"));
    }

    @Test
    public void testInvalidMembers1() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () ->getAcConfigurationForFile(getConfigurationMerger(), session, "invalid-unmanaged/test-invalid-unmanged-members1.yaml"));
    }
    
    @Test
    public void testInvalidMembers2() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> getAcConfigurationForFile(getConfigurationMerger(), session, "invalid-unmanaged/test-invalid-unmanged-members2.yaml"));
    }

}
