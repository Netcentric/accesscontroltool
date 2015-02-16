package biz.netcentric.cq.tools.actool.installhook;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class AcToolInstallHookServiceImplTest {
	
	@Test
	public void testExtractRunModesFromName() {
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
}
