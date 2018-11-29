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

    @Test (expected = NullPointerException.class)
    public void urlencode_null(){
        final String givenString = null;
        final String res = Utils.urlencode(givenString);
    }

    @Test
    public void urlencode_empty(){
        final String givenString = "";
        final String res = Utils.urlencode(givenString);
        Assert.assertEquals(givenString, res);
    }

    @Test
    public void urlencode_symbols(){
        final String givenString = "~!@ #$%^&()_+{ }:\"|[]\\|,./<>?";
        final String res = Utils.urlencode(givenString);
        Assert.assertEquals("%7E%21%40+%23%24%25%5E%26%28%29_%2B%7B+%7D%3A%22%7C%5B%5D%5C%7C%2C.%2F%3C%3E%3F", res);
    }

    @Test
    public void urlencode_basicAlphanumericals(){
        final String givenString = "TheQuickBrownFoxJumpsOverTheLazyDog1234567890.-*_";
        final String res = Utils.urlencode(givenString);
        Assert.assertEquals(givenString, res);
    }

    @Test (expected = NullPointerException.class)
    public void joinCollection_nullCollection(){
        String separator = "g";
        Collection<Object> objects = null;

        Utils.join(objects, separator);
    }

    @Test
    public void joinCollection_emptyCollection(){
        String separator = "g";
        Collection<Object> objects = new ArrayList<Object>() {};

        String res = Utils.join(objects, separator);
        Assert.assertEquals("", res);
    }

    @Test
    public void joinCollection_nullseparator(){
        String separator = null;
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("1");
        objects.add("2");
        objects.add("3");

        String res = Utils.join(objects, separator);
        Assert.assertEquals("1null2null3", res);
    }

    @Test
    public void joinCollection_emptyseparator(){
        String separator = "";
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("1");
        objects.add("2");
        objects.add("3");

        String res = Utils.join(objects, separator);
        Assert.assertEquals("123", res);
    }

    @Test
    public void joinCollection_simpleStrings(){
        String separator = "f";
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("11");
        objects.add("22");
        objects.add("33");

        String res = Utils.join(objects, separator);
        Assert.assertEquals("11f22f33", res);
    }

    @Test
    public void joinCollection_stringsWithNumbers(){
        String separator = "&";
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("str");
        objects.add("string");
        objects.add("int");
        objects.add(999);
        objects.add("float");
        objects.add(.2f);
        objects.add("long");
        objects.add(2L);
        objects.add("double");
        objects.add(.2);

        String res = Utils.join(objects, separator);
        Assert.assertEquals("str&string&int&999&float&0.2&long&2&double&0.2", res);
    }

    @Test
    public void uniqueTimestamp(){
        Set<Long> timestamps = new HashSet<>();
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        timestamps.add(Device.uniqueTimestamp());
        Assert.assertEquals(10, timestamps.size());
    }

    @Test
    public void currentDayOfWeek() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        Assert.assertEquals(Device.currentDayOfWeek(), day - 1);
    }

    @Test
    public void reflectiveClassExists() {
        Assert.assertTrue(Utils.reflectiveClassExists("java.lang.Integer"));
        Assert.assertFalse(Utils.reflectiveClassExists("lava.lang.Integer"));
    }

    @Test
    public void reflectiveCall() {
        Integer i = 5;
        String s = i.toString();
        Assert.assertEquals(s, Utils.reflectiveCall("java.lang.Integer", i, "toString"));
        Assert.assertEquals(i, Utils.reflectiveCall("java.lang.Integer", null, "parseInt", s));
        Assert.assertEquals(Boolean.FALSE, Utils.reflectiveCall("lava.lang.Integer", i, "nosuchclass"));
        Assert.assertEquals(Boolean.FALSE, Utils.reflectiveCall("java.lang.Integer", i, "nosuchmethod"));
        Assert.assertEquals(Boolean.FALSE, Utils.reflectiveCall("java.lang.Integer", null, "nosuchmethod", 0, 0));
        Assert.assertEquals(Boolean.FALSE, Utils.reflectiveCall("java.lang.Integer", null, "toUnsignedString", 0, 0));
    }

    @Test
    public void reflectiveSetField() {
        final class Testy {
            private int x = 10;
            Testy(int n) { x = n; }
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Testy && ((Testy)obj).x == x;
            }
        }
        Testy i = new Testy(5), j = new Testy(6);
        Assert.assertEquals(Boolean.FALSE, Utils.reflectiveSetField(i, "nosuchattr", j));
        Assert.assertEquals(Boolean.TRUE, Utils.reflectiveSetField(i, "x", 6));
        Assert.assertEquals(i, j);
    }

    @Test
    public void isEmpty() {
        Assert.assertFalse(Utils.isEmpty("notthatempty"));
        Assert.assertTrue(Utils.isEmpty(""));
        Assert.assertTrue(Utils.isEmpty(null));
    }

    @Test
    public void isNotEmpty() {
        Assert.assertTrue(Utils.isNotEmpty("notthatempty"));
        Assert.assertFalse(Utils.isNotEmpty(""));
        Assert.assertFalse(Utils.isNotEmpty(null));
    }
}