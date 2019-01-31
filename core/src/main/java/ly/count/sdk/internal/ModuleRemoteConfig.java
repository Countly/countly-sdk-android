package ly.count.sdk.internal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ModuleRemoteConfig extends ModuleBase {
    protected static final Log.Module L = Log.module("RemoteConfig");

    //after how much time the timeout error is returned
    Long rcRequestTimeout = 5000L;

    //if set to true, it will automatically download remote configs on module startup
    boolean automaticUpdateEnabled = false;

    public final static Long storableStorageId = 456L;
    public final static String storableStoragePrefix = "remote_config";

    protected InternalConfig internalConfig = null;
    protected CtxCore ctx = null;

    protected Map<Long, RemoteConfigCallback> requestCallbacks;

    //disabled is set when a empty module is created
    //in instances when the rating feature was not enabled
    //when a module is disabled, developer facing functions do nothing
    protected boolean disabledModule = false;

    @Override
    public Integer getFeature() {
        return CoreFeature.RemoteConfig.getIndex();
    }

    public interface RemoteConfigCallback {
        /**
         * Called after receiving remote config update result
         * @param error if is null, it means that no errors were encountered
         */
        void callback(String error);
    }

    @Override
    public void init(InternalConfig config) {
        super.init(config);
        requestCallbacks = new HashMap<Long, RemoteConfigCallback>();
    }

    @Override
    public void onContextAcquired(CtxCore ctx) {
        this.ctx = ctx;

        InternalConfig cfg = ctx.getConfig();

        Long timeoutVal = cfg.getRemoteConfigUpdateTimeoutLength();
        if(timeoutVal != null){
            this.rcRequestTimeout = timeoutVal;
        }

        Boolean automEnabled = cfg.getRemoteConfigAutomaticUpdateEnabled();
        if(automEnabled != null){
            automaticUpdateEnabled = automEnabled;
        }

        if(automaticUpdateEnabled){
            updateRemoteConfigValues(null, null,null);
        }
    }

    @Override
    public Boolean onRequest(Request request){
        //check if it's a old request or from this session
        if(requestCallbacks.containsKey(request.storageId())) {
            //indicate that this module is the owner
            request.own(ModuleRemoteConfig.class);
            //returned to indicate that the request is ready
            return true;

        } else {
            //no reference in callback map
            //assume that it probably is from a old session and therefore throw
            //this request away
            return false;
        }
    }

    public void disableModule(){
        disabledModule = true;
    }

    @Override
    public void onRequestCompleted(Request request, String response, int responseCode) {
        //check if we have a callback
        long receivedRequestId = request.storageId();
        boolean requestExists = requestCallbacks.containsKey(receivedRequestId);
        RemoteConfigCallback callback = requestCallbacks.get(receivedRequestId);
        String error = null;

        if (responseCode == 200) {
            //continue only if good response

            try {
                //interpret received changes
                JSONObject jobj = new JSONObject(response);

                //merge them into current values and save them
                RemoteConfigValueStore stored = getStoredValues();
                stored.mergeValues(jobj);
                saveStoredValues(stored);

            } catch (Exception e) {
                L.e("Failed merging new values into old ones", e);
                error = "Error merging results";
            }
        } else {
            //assume error
            L.w("onRequestCompleted, server returned failure code, response: [" + response + "]");
            error = "Server side error, [" + response + "]";
        }

        if(callback != null){
            callback.callback(error);
        }
    }

    /**
     * Internal call for updating remote config keys
     * @param keysOnly set if these are the only keys to update
     * @param keysExcept set if these keys should be ignored from the update
     * @param callback called after the update is done
     */
    protected void updateRemoteConfigValues(String[] keysOnly, String[] keysExcept, RemoteConfigCallback callback){
        String sKOnly = null;
        String sKExcept = null;

        if(keysOnly != null && keysOnly.length > 0){
            //include list takes precedence
            //if there is at least one item, use it
            JSONArray includeArray = new JSONArray();
            for (String key:keysOnly) {
                includeArray.put(key);
            }
            sKOnly = includeArray.toString();
        } else if(keysExcept != null && keysExcept.length > 0){
            //include list was not used, use the exclude list
            JSONArray excludeArray = new JSONArray();
            for(String key:keysExcept){
                excludeArray.put(key);
            }
            sKExcept = excludeArray.toString();
        }

        Request req = ModuleRequests.remoteConfigUpdate(ctx, sKOnly, sKExcept, ModuleRemoteConfig.class);
        requestCallbacks.put(req.storageId(), callback);
        ModuleRequests.pushAsync(ctx, req);
    }

    protected Object getRemoteConfigValue(String key){
        RemoteConfigValueStore values = getStoredValues();
        return values.getValue(key);
    }

    public RemoteConfigValueStore getStoredValues(){
        RemoteConfigValueStore rcvs = new RemoteConfigValueStore();
        Storage.read(ctx, rcvs);
        return rcvs;
    }

    public void saveStoredValues(RemoteConfigValueStore values){
        Storage.push(ctx, values);
    }

    public static class RemoteConfigValueStore implements Storable{
        public JSONObject values = new JSONObject();

        //add new values to the current storage
        public void mergeValues(JSONObject newValues){
            if(newValues == null) {return;}

            Iterator<String> iter = newValues.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                try {
                    Object value = newValues.get(key);
                    values.put(key, value);
                } catch (Exception e) {
                    Log.wtf("Failed merging new remote config values", e);
                }
            }
        }

        public Object getValue(String key){
            return values.opt(key);
        }

        @Override
        public Long storageId() {
            return storableStorageId;
        }

        @Override
        public String storagePrefix() {
            return storableStoragePrefix;
        }

        @Override
        public byte[] store() {
            try {
                return values.toString().getBytes(Utils.UTF8);
            } catch (UnsupportedEncodingException e) {
                L.wtf("UTF is not supported for RemoteConfigValueStore", e);
                return null;
            }
        }

        @Override
        public boolean restore(byte[] data) {
            try {
                String json = new String (data, Utils.UTF8);
                try {
                    values = new JSONObject(json);
                } catch (JSONException e) {
                    L.e("Couldn't decode RemoteConfigValueStore successfully", e);
                }
                return true;
            } catch (UnsupportedEncodingException e) {
                L.wtf("Cannot deserialize RemoteConfigValueStore", e);
            }

            return false;
        }
    }

    public class RemoteConfig {
        public void updateRemoteConfig(RemoteConfigCallback callback){
            L.d("Manually calling to updateRemoteConfig");
            if(disabledModule) { return; }
            ModuleRemoteConfig.this.updateRemoteConfigValues(null, null, callback);
        }

        public void updateRemoteConfigForKeysOnly(String[] keysOnly, RemoteConfigCallback callback){
            L.d("Manually calling to updateRemoteConfig with include keys");
            if(disabledModule) { return; }
            if (keysOnly == null) { L.w("updateRemoteConfigExceptKeys passed 'keys to include' array is null"); }
            ModuleRemoteConfig.this.updateRemoteConfigValues(keysOnly, null, callback);
        }

        public void updateRemoteConfigExceptKeys(String[] keysExclude, RemoteConfigCallback callback) {
            L.d("Manually calling to updateRemoteConfig with exclude keys");
            if (disabledModule) { return; }
            if (keysExclude == null) { L.w("updateRemoteConfigExceptKeys passed 'keys to ignore' array is null"); }
            ModuleRemoteConfig.this.updateRemoteConfigValues(null, keysExclude, callback);
        }

        public Object remoteConfigValueForKey(String key){
            L.d("Manually calling to remoteConfigValueForKey");
            if(disabledModule) { return null; }

            return ModuleRemoteConfig.this.getRemoteConfigValue(key);
        }
    }
}
