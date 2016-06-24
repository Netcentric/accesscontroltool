package biz.netcentric.cq.tools.actool.authorizableutils.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizableCreatorServiceImplTest {

    // class under test
    private AuthorizableCreatorServiceImpl cut;
    
    @Mock
    private UserManager userManager;

    @Mock
    private Authorizable acToolGroup;
    
    @Mock
    private Group externalGroup;
    
    @Before
    public void setup() {
        cut = new AuthorizableCreatorServiceImpl();
    }
    
    @Test
    public void testHandleExternalMembership() throws Exception {
        Mockito.when(userManager.getAuthorizable("acToolGroup")).thenReturn(acToolGroup);
        Mockito.when(userManager.getAuthorizable("externalGroup")).thenReturn(externalGroup);
        
        Map<Object, Object> properties = new HashMap<Object, Object>();
        properties.put(AuthorizableCreatorServiceImpl.PROPERTY_IGNORED_MEMBERSHIPS_PATTERN, "externalGroup");
        
        cut.activate(properties);
        cut.handleExternalMembership("acToolGroup", userManager, "externalGroup");
        Mockito.verify(externalGroup, Mockito.never()).removeMember(acToolGroup);
        
        properties.put(AuthorizableCreatorServiceImpl.PROPERTY_IGNORED_MEMBERSHIPS_PATTERN, "notMatchingRegEx");
        cut.modified(properties);
        cut.handleExternalMembership("acToolGroup", userManager, "externalGroup");
        Mockito.verify(externalGroup).removeMember(acToolGroup);
        
        Mockito.reset(externalGroup);
        properties.remove(AuthorizableCreatorServiceImpl.PROPERTY_IGNORED_MEMBERSHIPS_PATTERN);
        cut.modified(properties);
        cut.handleExternalMembership("acToolGroup", userManager, "externalGroup");
        Mockito.verify(externalGroup).removeMember(acToolGroup);
    }
    
}
