package ly.count.android.sdk.internal;

import android.content.*;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

@RunWith(AndroidJUnit4.class)
public class RequestsTests {
    @Before
    public void setupEveryTest(){

    }

    @After
    public void cleanupEveryTests(){
    }

    @Test
    public void dow_days(){
        Calendar calendar = new GregorianCalendar();

        calendar.set(2017, 0, 16);//monday
        Assert.assertEquals(1, Requests.dow(calendar));
        calendar.set(2017, 0, 17);
        Assert.assertEquals(2, Requests.dow(calendar));
        calendar.set(2017, 0, 18);
        Assert.assertEquals(3, Requests.dow(calendar));
        calendar.set(2017, 0, 19);
        Assert.assertEquals(4, Requests.dow(calendar));
        calendar.set(2017, 0, 20);
        Assert.assertEquals(5, Requests.dow(calendar));
        calendar.set(2017, 0, 21);
        Assert.assertEquals(6, Requests.dow(calendar));
        calendar.set(2017, 0, 22);
        Assert.assertEquals(0, Requests.dow(calendar));
    }
}