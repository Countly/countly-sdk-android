package ly.count.android.sdk;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TestUtils {

    public static String[] createStringArray(int count){
        String[] sArr = new String[count];
        Random rnd = new Random();

        for (int a = 0 ; a < sArr.length ; a++){
            sArr[a] = "" + rnd.nextInt();
        }

        return sArr;
    }

    public static Map<String, String> createMapString(int count){
        Map<String, String> mRes = new HashMap<>(count);
        Random rnd = new Random();

        for (int a = 0 ; a < count ; a++){
            mRes.put(""+rnd.nextInt(), ""+rnd.nextInt());
        }

        return mRes;
    }
}
