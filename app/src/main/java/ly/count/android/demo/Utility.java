package ly.count.android.demo;

import ly.count.android.sdk.Countly;

public class Utility {

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_1() {
        //keep this here, it's for proguard testing
    }

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_2() {
        //keep this here, it's for proguard testing
    }

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_3() {
        //keep this here, it's for proguard testing
    }

    public static void DeepCall_a() throws Exception {
        DeepCall_b();
    }

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_4() {
        //keep this here, it's for proguard testing
    }

    public static void DeepCall_b() throws Exception {
        try {
            try {
                try {
                    try {
                        try {
                            throw new Exception("Exception at the end of the call");
                        } catch (Exception ex) {
                            ArrayIndexOutOfBoundsException ai = new ArrayIndexOutOfBoundsException();
                            ai.initCause(ex);
                            throw ai;
                        }
                    } catch (Exception ex) {
                        ClassCastException cc = new ClassCastException();
                        cc.initCause(ex);
                        throw cc;
                    }
                } catch (Exception ex) {
                    ClassCastException cc = new ClassCastException();
                    cc.initCause(ex);
                    throw cc;
                }
            } catch (Exception ex) {
                ClassCastException cc = new ClassCastException();
                cc.initCause(ex);
                throw cc;
            }
        } catch (Exception ex) {
            ClassCastException cc = new ClassCastException();
            cc.initCause(ex);
            throw cc;
        }
    }

    @SuppressWarnings("EmptyMethod")
    void EmptyFunction_5() {
        //keep this here, it's for proguard testing
    }

    static void AnotherRecursiveCall(int amount) {
        if (amount > 0) {
            AnotherRecursiveCall(amount - 1);
        } else {
            Countly.sharedInstance().crashes().recordHandledException(new Exception("A handled recursive exception"));
        }
    }
}
