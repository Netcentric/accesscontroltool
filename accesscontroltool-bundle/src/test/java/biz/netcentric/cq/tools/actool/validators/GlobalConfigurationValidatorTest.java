package biz.netcentric.cq.tools.actool.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlobalConfigurationValidatorTest {

    @Test
    public void testVersionIsNewerOrEqualTo() {
        assertTrue(GlobalConfigurationValidator.versionIsNewerOrEqualTo("1.0.0", "1.0.0"));
        assertTrue(GlobalConfigurationValidator.versionIsNewerOrEqualTo("1.1.0", "1.0.0"));
        assertTrue(GlobalConfigurationValidator.versionIsNewerOrEqualTo("2.0.0", "1.0.0"));
        assertTrue(GlobalConfigurationValidator.versionIsNewerOrEqualTo("1.0.0.test", "1.0.0"));

        assertFalse(GlobalConfigurationValidator.versionIsNewerOrEqualTo("1.0.0", "1.1.0"));
    }

}
