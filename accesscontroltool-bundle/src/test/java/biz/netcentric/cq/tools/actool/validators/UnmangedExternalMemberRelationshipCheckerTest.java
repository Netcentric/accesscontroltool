package biz.netcentric.cq.tools.actool.validators;


import static biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMergerTest.getAcConfigurationForFile;
import static biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMergerTest.getConfigurationMerger;

import javax.jcr.Session;

import org.junit.Test;
import org.mockito.Mock;

public class UnmangedExternalMemberRelationshipCheckerTest {

    @Mock
    Session session;

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidIsMemberOf1() throws Exception {
        getAcConfigurationForFile(getConfigurationMerger(), session, "invalid-unmanaged/test-invalid-unmanged-isMemberOf1.yaml");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidIsMemberOf2() throws Exception {
        getAcConfigurationForFile(getConfigurationMerger(), session, "invalid-unmanaged/test-invalid-unmanged-isMemberOf2.yaml");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidMembers1() throws Exception {
        getAcConfigurationForFile(getConfigurationMerger(), session, "invalid-unmanaged/test-invalid-unmanged-members1.yaml");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidMembers2() throws Exception {
        getAcConfigurationForFile(getConfigurationMerger(), session, "invalid-unmanaged/test-invalid-unmanged-members2.yaml");
    }

}
