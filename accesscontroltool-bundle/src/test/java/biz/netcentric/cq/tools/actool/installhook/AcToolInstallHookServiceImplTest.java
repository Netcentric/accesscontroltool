package biz.netcentric.cq.tools.actool.installhook;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class AcToolInstallHookServiceImplTest {
	
	@Test
	public void testExtractRunModesFromName() {
		Assert.assertThat(AcToolInstallHookServiceImpl
				.extractRunModesFromName(""), Matchers
				.hasSize(0));
		Assert.assertThat(AcToolInstallHookServiceImpl
				.extractRunModesFromName("namewithoutrunmodes"), Matchers
				.hasSize(0));
		Assert.assertThat(AcToolInstallHookServiceImpl
				.extractRunModesFromName("name.runmode1"), Matchers
				.containsInAnyOrder("runmode1"));
		Assert.assertThat(AcToolInstallHookServiceImpl
				.extractRunModesFromName("name.runmode1.runmode2"), Matchers
				.containsInAnyOrder("runmode1", "runmode2"));
		Assert.assertThat(AcToolInstallHookServiceImpl
				.extractRunModesFromName("namewithoutrunmodes."), Matchers
				.hasSize(0));
		Assert.assertThat(AcToolInstallHookServiceImpl
				.extractRunModesFromName("name..runmode1"), Matchers
				.containsInAnyOrder("runmode1"));
	}
	
	@Test
	public void testIsRelevantConfiguration() {
		
		Set<String> currentRunmodes = new HashSet<String>(Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));
			
		Assert.assertFalse((AcToolInstallHookServiceImpl.isRelevantConfiguration("", "fragments", currentRunmodes)));
		Assert.assertFalse((AcToolInstallHookServiceImpl.isRelevantConfiguration("test", "fragments", currentRunmodes)));	
		Assert.assertTrue((AcToolInstallHookServiceImpl.isRelevantConfiguration("test.yaml", "fragments", currentRunmodes)));		
		Assert.assertFalse((AcToolInstallHookServiceImpl.isRelevantConfiguration("test.yaml", "fragments.publish", currentRunmodes)));		
		Assert.assertTrue((AcToolInstallHookServiceImpl.isRelevantConfiguration("test.yaml", "fragments.author", currentRunmodes)));		
		Assert.assertTrue((AcToolInstallHookServiceImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent", currentRunmodes)));
		Assert.assertFalse((AcToolInstallHookServiceImpl.isRelevantConfiguration("test.yam", "fragments.samplecontent", currentRunmodes)));
		Assert.assertFalse((AcToolInstallHookServiceImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent.publish", currentRunmodes)));
		Assert.assertFalse((AcToolInstallHookServiceImpl.isRelevantConfiguration("test.yaml", "fragments.foo.publish", currentRunmodes)));
		Assert.assertTrue((AcToolInstallHookServiceImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent.local", currentRunmodes)));
	}
}
