package biz.netcentric.cq.tools.actool.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValidatorsTest {

	@Test
	public void isValidAuthorizableNameTest(){
		
		assertTrue(Validators.isValidAuthorizableName("group-A"));
		assertTrue(Validators.isValidAuthorizableName("Group-1"));
		
		assertFalse(Validators.isValidAuthorizableName("group_A"));
		assertFalse(Validators.isValidAuthorizableName("group.A"));
		assertFalse(Validators.isValidAuthorizableName("group A"));
		assertFalse(Validators.isValidAuthorizableName(""));
		assertFalse(Validators.isValidAuthorizableName(null));
	}
	
	@Test
	public void isValidActionTest(){
		String[] actionStrings = {"read", "modify", "create", "delete", "acl_read", "acl_edit", "replicate"};
		
		for(String action : actionStrings){
			assertTrue(Validators.isValidAction(action));
		}
		
		assertFalse(Validators.isValidAction("write"));
		assertFalse(Validators.isValidAction("Read"));
		assertFalse(Validators.isValidAction("aclEdit"));
		
		assertFalse(Validators.isValidAction(null));
	}
	
	@Test
	public void isValidPermissionTest(){
	
		assertTrue(Validators.isValidPermission("allow"));
		assertTrue(Validators.isValidPermission("deny"));
		
		assertFalse(Validators.isValidPermission("Allow"));
		assertFalse(Validators.isValidPermission("Deny"));
		assertFalse(Validators.isValidPermission("write"));
		
		assertFalse(Validators.isValidPermission(null));
	}
	
	@Test
	public void isValidRepGlobTest(){
		assertTrue(Validators.isValidRegex("*/jcr:content*"));
		assertTrue(Validators.isValidRegex("*/content/*"));
		assertFalse(Validators.isValidRegex("["));
	}
}
