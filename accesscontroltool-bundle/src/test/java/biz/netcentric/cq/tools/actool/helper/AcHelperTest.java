package biz.netcentric.cq.tools.actool.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class AcHelperTest {

	AceBean aceBeanGroupA_1;
	AceBean aceBeanGroupA_2;
	AceBean aceBeanGroupA_3;
	AceBean aceBeanGroupB_1;
	AceBean aceBeanGroupB_2;
	AceBean aceBeanGroupB_3;
	AceBean aceBeanGroupC_1;
	AceBean aceBeanGroupD_1;
	AceBean aceBeanGroupE_1;

	@Before
	public void setup() {
		// ACEs from groups contained in config

		aceBeanGroupA_1 = new AceBean();
		aceBeanGroupA_1.setPrincipal("group-A");
		aceBeanGroupA_1.setActions(new String[]{"allow","replicate"});
		aceBeanGroupA_1.setAllow(false);
		aceBeanGroupA_1.setJcrPath("/content");
		aceBeanGroupA_1.setPrivilegesString("");
		aceBeanGroupA_1.setRepGlob("");

		aceBeanGroupA_2 = new AceBean();
		aceBeanGroupA_2.setPrincipal("group-A");
		aceBeanGroupA_2.setActions(new String[]{"allow,modify"});
		aceBeanGroupA_2.setAllow(true);
		aceBeanGroupA_2.setJcrPath("/content");
		aceBeanGroupA_2.setPrivilegesString("");
		aceBeanGroupA_2.setRepGlob("");

		aceBeanGroupA_3 = new AceBean();
		aceBeanGroupA_3.setPrincipal("group-A");
		aceBeanGroupA_3.setActions(new String[]{"allow,modify"});
		aceBeanGroupA_3.setAllow(false);
		aceBeanGroupA_3.setJcrPath("/content/isp");
		aceBeanGroupA_3.setPrivilegesString("");
		aceBeanGroupA_3.setRepGlob("");

		aceBeanGroupB_1 = new AceBean();
		aceBeanGroupB_1.setPrincipal("group-B");
		aceBeanGroupB_1.setActions(new String[]{"allow"});
		aceBeanGroupB_1.setAllow(true);
		aceBeanGroupB_1.setJcrPath("/content");
		aceBeanGroupB_1.setPrivilegesString("");
		aceBeanGroupB_1.setRepGlob("");

		aceBeanGroupB_2 = new AceBean();
		aceBeanGroupB_2.setPrincipal("group-B");
		aceBeanGroupB_2.setActions(new String[]{"allow,delete"});
		aceBeanGroupB_2.setAllow(false);
		aceBeanGroupB_2.setJcrPath("/content");
		aceBeanGroupB_2.setPrivilegesString("");
		aceBeanGroupB_2.setRepGlob("");

		aceBeanGroupB_3 = new AceBean();
		aceBeanGroupB_3.setPrincipal("group-B");
		aceBeanGroupB_3.setActions(new String[]{"allow,delete"});
		aceBeanGroupB_3.setAllow(true);
		aceBeanGroupB_3.setJcrPath("/content/isp");
		aceBeanGroupB_3.setPrivilegesString("");
		aceBeanGroupB_3.setRepGlob("");

		aceBeanGroupC_1 = new AceBean();
		aceBeanGroupC_1.setPrincipal("group-C");
		aceBeanGroupC_1.setActions(new String[]{"allow"});
		aceBeanGroupC_1.setAllow(true);
		aceBeanGroupC_1.setJcrPath("/content");
		aceBeanGroupC_1.setPrivilegesString("");
		aceBeanGroupC_1.setRepGlob("");

		// ACEs from Groups not contained in config

		aceBeanGroupD_1 = new AceBean();
		aceBeanGroupD_1.setPrincipal("group-D");
		aceBeanGroupD_1.setActions(new String[]{"allow"});
		aceBeanGroupD_1.setAllow(true);
		aceBeanGroupD_1.setJcrPath("/content");
		aceBeanGroupD_1.setPrivilegesString("");
		aceBeanGroupD_1.setRepGlob("");

		aceBeanGroupE_1 = new AceBean();
		aceBeanGroupE_1.setPrincipal("group-E");
		aceBeanGroupE_1.setActions(new String[]{"allow"});
		aceBeanGroupE_1.setAllow(true);
		aceBeanGroupE_1.setJcrPath("/content");
		aceBeanGroupE_1.setPrivilegesString("");
		aceBeanGroupE_1.setRepGlob("");
	}

	@Test
	public void getMergedACLTest(){
		
		Set<AceBean> aclfromConfig = new HashSet<AceBean>();
		Set<AceBean> aclFomRepository = new HashSet<AceBean>();
		Set<String> authorizablesSet = new HashSet<String>();

		authorizablesSet.add("group-A");
		authorizablesSet.add("group-B");
		authorizablesSet.add("group-C");

		// groups in config: group-A, group-B, group-C

		// basic assertion: the ACE set from config is never empty
		// gets used for all following tests
		
		aclfromConfig.add(aceBeanGroupA_1);
		aclfromConfig.add(aceBeanGroupB_1);

		// case1: same groups in ACL from repo

		aclFomRepository.add(aceBeanGroupA_1);
		aclFomRepository.add(aceBeanGroupB_1);

		Set<AceBean> resultSet = new HashSet<AceBean>();

		resultSet.add(aceBeanGroupA_1);
		resultSet.add(aceBeanGroupB_1);

		assertEquals(resultSet, AcHelper.getMergedACL(aclfromConfig, aclFomRepository, authorizablesSet)); 


		aclFomRepository.clear();
		resultSet.clear();

		// case2: old obsolete ACE (A_2) in repo gets deleted, new ACEs (A_1, B_2) get added

		aclFomRepository.add(aceBeanGroupA_1);
		aclFomRepository.add(aceBeanGroupA_2);

		resultSet.add(aceBeanGroupA_1);
		resultSet.add(aceBeanGroupB_1);

		assertEquals(resultSet, AcHelper.getMergedACL(aclfromConfig, aclFomRepository, authorizablesSet)); 

		aclFomRepository.clear();
		resultSet.clear();

		// case3:  old obsolete ACE (A_2) in repo gets deleted, D_1 stays since its no group from config
		//         B_2 gets added

		aclFomRepository.add(aceBeanGroupA_2); // Group-A
		aclFomRepository.add(aceBeanGroupD_1); // Group-D

		resultSet.add(aceBeanGroupA_1); // Group-A
		resultSet.add(aceBeanGroupB_1); // Group-B
		resultSet.add(aceBeanGroupD_1); // Group-D

		assertEquals(resultSet, AcHelper.getMergedACL(aclfromConfig, aclFomRepository, authorizablesSet)); 


		// case4: D_1 AND(!) E_1 stay since they're no groups from config,

		aclFomRepository.add(aceBeanGroupD_1); 
		aclFomRepository.add(aceBeanGroupE_1); 

		resultSet.add(aceBeanGroupA_1); 
		resultSet.add(aceBeanGroupB_1); 
		resultSet.add(aceBeanGroupD_1); 
		// E_1 not in result set -> wrong

		assertNotEquals(resultSet, AcHelper.getMergedACL(aclfromConfig, aclFomRepository, authorizablesSet)); 

		// case4: empty ACL in repository -> A_1 and B_1 get added

		aclFomRepository.clear();
		resultSet.clear();

		resultSet.add(aceBeanGroupA_1); // Group-A
		resultSet.add(aceBeanGroupB_1); // Group-B

		assertEquals(resultSet, AcHelper.getMergedACL(aclfromConfig, aclFomRepository, authorizablesSet));
	}

	@Test
	public void getPathBasedAceMapTest(){

		Map<String, Set<AceBean>> groupBasedAceMap = new HashMap<String, Set<AceBean>>();
		Map<String, Set<AceBean>> pathBasedAceMap = new HashMap<String, Set<AceBean>>();


		// case1: no ordered Set 

		// group based map

		groupBasedAceMap.put("group-A", new HashSet<AceBean>());
		groupBasedAceMap.get("group-A").add(aceBeanGroupA_1);
		groupBasedAceMap.get("group-A").add(aceBeanGroupA_3);



		groupBasedAceMap.put("group-B", new HashSet<AceBean>());
		groupBasedAceMap.get("group-B").add(aceBeanGroupB_1);
		groupBasedAceMap.get("group-B").add(aceBeanGroupB_3);


		// expected result map (unordered)

		pathBasedAceMap.put("/content", new HashSet<AceBean>());

		pathBasedAceMap.get("/content").add(aceBeanGroupA_1);
		pathBasedAceMap.get("/content").add(aceBeanGroupB_1);


		pathBasedAceMap.put("/content/isp", new HashSet<AceBean>());

		pathBasedAceMap.get("/content/isp").add(aceBeanGroupA_3);
		pathBasedAceMap.get("/content/isp").add(aceBeanGroupB_3);

		assertEquals(pathBasedAceMap, AcHelper.getPathBasedAceMap(groupBasedAceMap, 2)); // 1: TreeSet, 2: HashSet


		// case 2: ordered Set (deny-ACEs before allow ACEs)

		groupBasedAceMap = new HashMap<String, Set<AceBean>>();
		pathBasedAceMap = new HashMap<String, Set<AceBean>>();


		groupBasedAceMap.put("group-A",  new LinkedHashSet<AceBean>());
		groupBasedAceMap.get("group-A").add(aceBeanGroupA_1); // deny, /content
		groupBasedAceMap.get("group-A").add(aceBeanGroupA_2); // true, /content
		groupBasedAceMap.get("group-A").add(aceBeanGroupA_3); // deny, /content/isp


		groupBasedAceMap.put("group-B", new LinkedHashSet<AceBean>());
		groupBasedAceMap.get("group-B").add(aceBeanGroupB_1); // allow, /content
		groupBasedAceMap.get("group-B").add(aceBeanGroupB_3); // allow, /content/isp
		groupBasedAceMap.get("group-B").add(aceBeanGroupB_2); // deny, /content



		pathBasedAceMap.put("/content", new LinkedHashSet<AceBean>());

		pathBasedAceMap.get("/content").add(aceBeanGroupB_2); // deny, /content
		pathBasedAceMap.get("/content").add(aceBeanGroupA_1); // deny, /content
		pathBasedAceMap.get("/content").add(aceBeanGroupB_1); // allow, /content
		pathBasedAceMap.get("/content").add(aceBeanGroupA_2); // allow, /content
		
		pathBasedAceMap.put("/content/isp",  new LinkedHashSet<AceBean>());

		pathBasedAceMap.get("/content/isp").add(aceBeanGroupA_3); // deny, /content/isp
		pathBasedAceMap.get("/content/isp").add(aceBeanGroupB_3); // allow, /content/isp

		// Map.equals() compares the  sizes of the entry sets, and then does set1.containsAll(set2) which is insufficient for testing a special order of the set elements

		Map<String, Set<AceBean>> resultMap = AcHelper.getPathBasedAceMap(groupBasedAceMap, 1);

		Collection<AceBean> listBeansexpected = new ArrayList<AceBean>();
		Collection<AceBean> listBeansResult = new ArrayList<AceBean>();
		
		// loop through each map save the beans in a list and then compare the single elements
		
		for(Map.Entry<String, Set<AceBean>> aceSet : pathBasedAceMap.entrySet()){
			for(AceBean bean : aceSet.getValue()){
				listBeansexpected.add(bean);
			}
		}
		
		for(Map.Entry<String, Set<AceBean>> aceSet : resultMap.entrySet()){
			for(AceBean bean : aceSet.getValue()){
				listBeansResult.add(bean);
			}
		}
		
		assertEquals(listBeansexpected, listBeansResult);
	}
}
