package biz.netcentric.cq.tools.actool.configreader;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class ConfigFilesRetrieverImplTest {

    @Test
    public void testExtractRunModesFromName() {
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName(""), Matchers
                .hasSize(0));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("namewithoutrunmodes"), Matchers
                .hasSize(0));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("name.runmode1"), Matchers
                .containsInAnyOrder("runmode1"));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("name.runmode1.runmode2"), Matchers
                .containsInAnyOrder("runmode1", "runmode2"));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("namewithoutrunmodes."), Matchers
                .hasSize(0));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("name..runmode1"), Matchers
                .containsInAnyOrder("runmode1"));
    }

    @Test
    public void testIsRelevantConfiguration() {

        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));

        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("", "fragments", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test", "fragments", currentRunmodes)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.publish", currentRunmodes)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.author", currentRunmodes)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yam", "fragments.samplecontent", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent.publish",
                currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.foo.publish", currentRunmodes)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent.local", currentRunmodes)));

    }

    @Test
    public void testIsRelevantConfigurationOrCombinations() {
        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));

        // testing 'or' combinations with
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.dev,local", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.int,prod", currentRunmodes)));

        // combined 'and' and 'or'
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.author.dev,local", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.publish.dev,local", currentRunmodes)));

    }

}
