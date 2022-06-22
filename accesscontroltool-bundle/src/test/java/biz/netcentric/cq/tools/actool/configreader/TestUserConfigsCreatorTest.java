package biz.netcentric.cq.tools.actool.configreader;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

public class TestUserConfigsCreatorTest {

    private static final String TEST_GROUP_ID = "test-group-id";
    private static final String TEST_GROUP_NAME = "Test Group Name";
    private static final String TEST_GROUP_PATH = "/home/groups/testfolder/subfolder";
    
    TestUserConfigsCreator testUserConfigsCreator = new TestUserConfigsCreator();
    
    @Test
    public void testBasicInterpolation() {

        Map<String, Object> testVars = getTestVars();
        assertEquals(TEST_GROUP_ID, testUserConfigsCreator.processValue("%{group.id}", testVars));
        assertEquals("Prefix "+ TEST_GROUP_NAME, testUserConfigsCreator.processValue("Prefix %{group.name}", testVars));
        assertEquals(TEST_GROUP_PATH, testUserConfigsCreator.processValue("%{group.path}", testVars));
    }
    
    @Test
    public void testValuesRemainUnchanged() {
        Map<String, Object> testVars = getTestVars();
        assertEquals("Simple Text", testUserConfigsCreator.processValue("Simple Text", testVars));
        assertEquals("{dd2a1c0550fa4750b353a61be823ccd3f8e525c3cdf8c7c359a717cc1785ebaf}", testUserConfigsCreator.processValue("{dd2a1c0550fa4750b353a61be823ccd3f8e525c3cdf8c7c359a717cc1785ebaf}", testVars));
    }

    @Test
    public void testGroupWithoutNameFallsBackToId() {
        assertEquals(TEST_GROUP_ID, testUserConfigsCreator.processValue("%{group.name}", getTestVars(null)));
    }

    @Test
    public void testELFunctions() {
        Map<String, Object> testVars = getTestVars();
        assertEquals("testfolder/subfolder", testUserConfigsCreator.processValue("%{split(group.path,'/')[2]}/%{split(group.path,'/')[3]}", testVars));
        assertEquals("group-id", testUserConfigsCreator.processValue("%{substringAfter(group.id,'-')}", testVars));
    }
    

    @Test
    public void testMultipleReplacements() {
        assertEquals("Name "+TEST_GROUP_NAME+" Path "+TEST_GROUP_PATH, testUserConfigsCreator.processValue("Name %{group.name} Path %{group.path}", getTestVars()));
    }


    private Map<String, Object> getTestVars() {
        return getTestVars(TEST_GROUP_NAME);
    }
    private Map<String, Object> getTestVars(String name) {
        AuthorizableConfigBean groupAuthConfigBean = new AuthorizableConfigBean();
        groupAuthConfigBean.setAuthorizableId(TEST_GROUP_ID);
        groupAuthConfigBean.setName(name);
        groupAuthConfigBean.setPath(TEST_GROUP_PATH);
        return testUserConfigsCreator.getVarsForAuthConfigBean(groupAuthConfigBean);
    }

}
