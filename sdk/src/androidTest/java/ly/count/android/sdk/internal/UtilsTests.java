package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;


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
        String seperator = "g";
        Collection<Object> objects = null;

        Utils.join(objects, seperator);
    }

    @Test
    public void joinCollection_emptyCollection(){
        String seperator = "g";
        Collection<Object> objects = new ArrayList<Object>() {};

        String res = Utils.join(objects, seperator);
        Assert.assertEquals("", res);
    }

    @Test
    public void joinCollection_nullSeperator(){
        String seperator = null;
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("1");
        objects.add("2");
        objects.add("3");

        String res = Utils.join(objects, seperator);
        Assert.assertEquals("1null2null3null", res);
    }

    @Test
    public void joinCollection_emptySeperator(){
        String seperator = "";
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("1");
        objects.add("2");
        objects.add("3");

        String res = Utils.join(objects, seperator);
        Assert.assertEquals("123", res);
    }

    @Test
    public void joinCollection_simpleStrings(){
        String seperator = "f";
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("11");
        objects.add("22");
        objects.add("33");

        String res = Utils.join(objects, seperator);
        Assert.assertEquals("11f22f33f", res);
    }
}