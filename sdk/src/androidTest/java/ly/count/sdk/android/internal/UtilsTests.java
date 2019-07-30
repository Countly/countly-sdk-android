package ly.count.sdk.android.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


@RunWith(AndroidJUnit4.class)
public class UtilsTests {
    @Before
    public void setupEveryTest(){

    }

    @After
    public void cleanupEveryTests(){

    }

    @Test
    public void currentDayOfWeek() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        junit.framework.Assert.assertEquals(Device.dev.currentDayOfWeek(), day - 1);
    }

    @Test
    public void uniqueTimestamp(){
        Set<Long> timestamps = new HashSet<>();
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        timestamps.add(Device.dev.uniqueTimestamp());
        junit.framework.Assert.assertEquals(10, timestamps.size());
    }
}