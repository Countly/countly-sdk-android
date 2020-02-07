package ly.count.android.sdk;

abstract class ModuleBase {
    Countly _cly;

    ModuleBase(Countly cly){
        _cly = cly;
    }

    void halt(){
        throw new UnsupportedOperationException();
    }
}
