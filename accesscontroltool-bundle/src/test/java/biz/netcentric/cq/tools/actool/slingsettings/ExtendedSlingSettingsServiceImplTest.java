package biz.netcentric.cq.tools.actool.slingsettings;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class ExtendedSlingSettingsServiceImplTest {

    @Test
    public void testGetBestRunModeMatchCountFromSpec() {
        Assert.assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", Collections.singleton("test5")));
        Assert.assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", ImmutableSet.of("test1", "test3")));
        Assert.assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", ImmutableSet.of("test2", "test3")));
        Assert.assertEquals(2, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", ImmutableSet.of("test1", "test2")));
        Assert.assertEquals(2, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", ImmutableSet.of("test2", "test4")));
        Assert.assertEquals(3, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4,test5.test6.test7", ImmutableSet.of("test1", "test2", "test4", "test5", "test6", "test7")));
    }
}
