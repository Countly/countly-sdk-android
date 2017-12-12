package ly.count.android.demo;


import ly.count.android.sdk.Countly;

public class Utility {

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_1(){
        //keep this here, it's for proguard testing
    }

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_2(){
        //keep this here, it's for proguard testing
    }

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_3(){
        //keep this here, it's for proguard testing
    }

    public static void DeepCall_a() throws Exception{
        DeepCall_b();
    }

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_4(){
        //keep this here, it's for proguard testing
    }

    public static void DeepCall_b() throws Exception{
        throw new Exception("Exception at the end of the call");
    }

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_5(){
        //keep this here, it's for proguard testing
    }

    static void AnotherRecursiveCall(int amount){
        if(amount > 0){
            AnotherRecursiveCall(amount - 1);
        } else {
            Countly.sharedInstance().logException(new Exception("A handled recursive exception"));
        }
    }
}
