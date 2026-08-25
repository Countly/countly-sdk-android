package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConfigSdkInternalLimitsTests {

    /**
     * Every Countly instance keeps its own limits, copied from the developer's config at init, because the
     * server behaviour settings resolve them per instance. That copy is hand-written, so this test walks the
     * declared fields and fails when one of them is not copied - which is what would otherwise happen
     * silently the next time someone adds a limit, leaving the new limit shared through the config again.
     */
    @Test
    public void copyFrom_copiesEveryDeclaredField() throws Exception {
        ConfigSdkInternalLimits source = new ConfigSdkInternalLimits();
        ConfigSdkInternalLimits target = new ConfigSdkInternalLimits();

        List<Field> copied = new ArrayList<>();
        int distinctValue = 11;

        for (Field field : ConfigSdkInternalLimits.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Class<?> type = field.getType();
            if (type == Integer.class || type == int.class) {
                //a value distinct from both the default and every other field, so a copy that assigns the
                //wrong field is caught as well as one that assigns nothing
                field.set(source, distinctValue);
                distinctValue += 7;
                copied.add(field);
            } else {
                Assert.fail("ConfigSdkInternalLimits." + field.getName() + " has unhandled type " + type
                    + ". Extend copyFrom() and this test to cover it.");
            }
        }

        Assert.assertFalse("no fields found - this test would prove nothing", copied.isEmpty());

        target.copyFrom(source);

        for (Field field : copied) {
            Assert.assertEquals("copyFrom() did not copy '" + field.getName()
                + "'. Add it to ConfigSdkInternalLimits.copyFrom(), otherwise instances share that limit.",
                field.get(source), field.get(target));
        }
    }

    /**
     * The minimum clamping moved out of Countly#onSdkConfigurationChanged and onto the limits themselves, so
     * a server sending 0 or a negative limit can not make the SDK truncate everything to nothing. Limits that
     * were never set stay unset - null means "use the SDK default", not "clamp me to 1".
     */
    @Test
    public void clampToMinimums_raisesSetLimitsBelowOne_andLeavesUnsetOnesAlone() {
        ConfigSdkInternalLimits limits = new ConfigSdkInternalLimits();

        limits.maxKeyLength = 0;
        limits.maxValueSize = -5;
        limits.maxSegmentationValues = 1;
        limits.maxBreadcrumbCount = 40;
        //maxStackTraceLinesPerThread and maxStackTraceLineLength deliberately left null

        limits.clampToMinimums();

        Assert.assertEquals(Integer.valueOf(1), limits.maxKeyLength);
        Assert.assertEquals(Integer.valueOf(1), limits.maxValueSize);
        Assert.assertEquals("a limit already at the minimum is untouched", Integer.valueOf(1), limits.maxSegmentationValues);
        Assert.assertEquals("a valid limit is untouched", Integer.valueOf(40), limits.maxBreadcrumbCount);
        Assert.assertNull("an unset limit must stay unset so the SDK default applies", limits.maxStackTraceLinesPerThread);
        Assert.assertNull("an unset limit must stay unset so the SDK default applies", limits.maxStackTraceLineLength);
    }
}
