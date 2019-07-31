package ly.count.sdk.internal;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(JUnit4.class)
public class UtilsTests {
    @Before
    public void setupEveryTest(){
    }

    @After
    public void cleanupEveryTests(){
    }

    @Test
    public void base_64_decodeToString(){
        String decodeSource = "MTIzNDU=";
        String decodeTarget = "12345";

        Assert.assertEquals(decodeTarget, Utils.Base64.decodeToString(decodeSource));
    }

    @Test
    public void base_64_decodeToByte(){
        String decodeSource = "MTIzNDU=";
        String decodeTarget = "12345";
        byte[] decodeTargetBytes = decodeTarget.getBytes();
        byte[] resBytes = Utils.Base64.decode(decodeSource);

        Assert.assertArrayEquals(decodeTargetBytes, resBytes);
    }

    @Test
    public void base_64_encodeByte() {
        String source = "12345";
        byte[] sourceBytes = source.getBytes();
        String resTarget = "MTIzNDU=";

        Assert.assertEquals(resTarget, Utils.Base64.encode(sourceBytes));
    }

    @Test
    public void base_64_encodeString(){
        String source = "12345";
        String resTarget = "MTIzNDU=";

        Assert.assertEquals(resTarget, Utils.Base64.encode(source));
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
        junit.framework.Assert.assertEquals(givenString, res);
    }

    @Test
    public void urlencode_symbols(){
        final String givenString = "~!@ #$%^&()_+{ }:\"|[]\\|,./<>?";
        final String res = Utils.urlencode(givenString);
        junit.framework.Assert.assertEquals("%7E%21%40+%23%24%25%5E%26%28%29_%2B%7B+%7D%3A%22%7C%5B%5D%5C%7C%2C.%2F%3C%3E%3F", res);
    }

    @Test
    public void urlencode_basicAlphanumericals(){
        final String givenString = "TheQuickBrownFoxJumpsOverTheLazyDog1234567890.-*_";
        final String res = Utils.urlencode(givenString);
        junit.framework.Assert.assertEquals(givenString, res);
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
        junit.framework.Assert.assertEquals("", res);
    }

    @Test
    public void joinCollection_nullseparator(){
        String separator = null;
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("1");
        objects.add("2");
        objects.add("3");

        String res = Utils.join(objects, separator);
        junit.framework.Assert.assertEquals("1null2null3", res);
    }

    @Test
    public void joinCollection_emptyseparator(){
        String separator = "";
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("1");
        objects.add("2");
        objects.add("3");

        String res = Utils.join(objects, separator);
        junit.framework.Assert.assertEquals("123", res);
    }

    @Test
    public void joinCollection_simpleStrings(){
        String separator = "f";
        Collection<Object> objects = new ArrayList<Object>() {};
        objects.add("11");
        objects.add("22");
        objects.add("33");

        String res = Utils.join(objects, separator);
        junit.framework.Assert.assertEquals("11f22f33", res);
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
        junit.framework.Assert.assertEquals("str&string&int&999&float&0.2&long&2&double&0.2", res);
    }

    @Test
    public void reflectiveClassExists() {
        junit.framework.Assert.assertTrue(Utils.reflectiveClassExists("java.lang.Integer"));
        junit.framework.Assert.assertFalse(Utils.reflectiveClassExists("lava.lang.Integer"));
    }

    @Test
    public void reflectiveCall() {
        Integer i = 5;
        String s = i.toString();
        junit.framework.Assert.assertEquals(s, Utils.reflectiveCall("java.lang.Integer", i, "toString"));
        junit.framework.Assert.assertEquals(i, Utils.reflectiveCall("java.lang.Integer", null, "parseInt", s));
        junit.framework.Assert.assertEquals(Boolean.FALSE, Utils.reflectiveCall("lava.lang.Integer", i, "nosuchclass"));
        junit.framework.Assert.assertEquals(Boolean.FALSE, Utils.reflectiveCall("java.lang.Integer", i, "nosuchmethod"));
        junit.framework.Assert.assertEquals(Boolean.FALSE, Utils.reflectiveCall("java.lang.Integer", null, "nosuchmethod", 0, 0));
        junit.framework.Assert.assertEquals(Boolean.FALSE, Utils.reflectiveCall("java.lang.Integer", null, "toUnsignedString", 0, 0));
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
        junit.framework.Assert.assertEquals(Boolean.FALSE, Utils.reflectiveSetField(i, "nosuchattr", j));
        junit.framework.Assert.assertEquals(Boolean.TRUE, Utils.reflectiveSetField(i, "x", 6));
        junit.framework.Assert.assertEquals(i, j);
    }

    @Test
    public void isEmpty() {
        junit.framework.Assert.assertFalse(Utils.isEmpty("notthatempty"));
        junit.framework.Assert.assertTrue(Utils.isEmpty(""));
        junit.framework.Assert.assertTrue(Utils.isEmpty(null));
    }

    @Test
    public void isNotEmpty() {
        junit.framework.Assert.assertTrue(Utils.isNotEmpty("notthatempty"));
        junit.framework.Assert.assertFalse(Utils.isNotEmpty(""));
        junit.framework.Assert.assertFalse(Utils.isNotEmpty(null));
    }
}
