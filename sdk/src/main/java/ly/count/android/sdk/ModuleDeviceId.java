package ly.count.android.sdk;

import android.util.Log;

class ModuleDeviceId extends ModuleBase {

    ModuleDeviceId(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleDeviceId] Initialising");
        }
    }

    private void exitTemporaryIdMode(DeviceId.Type type, String deviceId){
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "Calling exitTemporaryIdMode");
        }

        if (!_cly.isInitialized()) {
            throw new IllegalStateException("init must be called before exitTemporaryIdMode");
        }

        //start by changing stored ID
        _cly.connectionQueue_.getDeviceId().changeToId(_cly.context_, _cly.connectionQueue_.getCountlyStore(), type, deviceId);

        //update stored request for ID change to use this new ID
        String[] storedRequests = _cly.connectionQueue_.getCountlyStore().connections();
        String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;
        String newIdTag = "&device_id=" + deviceId;

        boolean foundOne = false;
        for(int a = 0 ; a < storedRequests.length ; a++){
            if(storedRequests[a].contains(temporaryIdTag)){
                if (_cly.isLoggingEnabled()) {
                    Log.d(Countly.TAG, "[exitTemporaryIdMode] Found a tag to replace in: [" + storedRequests[a] + "]");
                }
                storedRequests[a] = storedRequests[a].replace(temporaryIdTag, newIdTag);
                foundOne = true;
            }
        }

        if(foundOne){
            _cly.connectionQueue_.getCountlyStore().replaceConnections(storedRequests);
        }

        //update remote config_ values if automatic update is enabled
        _cly.remoteConfigClearValues();
        if (_cly.remoteConfigAutomaticUpdateEnabled && _cly.anyConsentGiven()) {
            _cly.moduleRemoteConfig.updateRemoteConfigValues(null, null, _cly.connectionQueue_, false, null);
        }

        _cly.doStoredRequests();
    }

    /**
     * Changes current device id type to the one specified in parameter. Closes current session and
     * reopens new one with new id. Doesn't merge user profiles on the server
     * @param type Device ID type to change to
     * @param deviceId Optional device ID for a case when type = DEVELOPER_SPECIFIED
     */
    void changeDeviceIdWithoutMerge(DeviceId.Type type, String deviceId) {
        if (type == null) {
            throw new IllegalStateException("type cannot be null");
        }

        if(!_cly.anyConsentGiven()){
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "Can't change Device ID if no consent is given");
            }
            return;
        }

        DeviceId currentDeviceId = _cly.connectionQueue_.getDeviceId();

        if(currentDeviceId.temporaryIdModeEnabled() && (deviceId != null && deviceId.equals(DeviceId.temporaryCountlyDeviceId))){
            // we already are in temporary mode and we want to set temporary mode
            // in this case we just ignore the request since nothing has to be done
            return;
        }

        if(currentDeviceId.temporaryIdModeEnabled() || _cly.connectionQueue_.queueContainsTemporaryIdItems()){
            // we are about to exit temporary ID mode
            // because of the previous check, we know that the new type is a different one
            // we just call our method for exiting it
            // we don't end the session, we just update the device ID and connection queue
            exitTemporaryIdMode(type, deviceId);
        }


        // we are either making a simple ID change or entering temporary mode
        // in both cases we act the same as the temporary ID requests will be updated with the final ID later

        //force flush events so that they are associated correctly
        _cly.sendEventsForced();

        _cly.moduleSessions.endSessionInternal(currentDeviceId.getId());
        currentDeviceId.changeToId(_cly.context_, _cly.connectionQueue_.getCountlyStore(), type, deviceId);
        _cly.moduleSessions.beginSessionInternal();

        //update remote config_ values if automatic update is enabled
        _cly.remoteConfigClearValues();
        if (_cly.remoteConfigAutomaticUpdateEnabled && _cly.anyConsentGiven()) {
            _cly.moduleRemoteConfig.updateRemoteConfigValues(null, null, _cly.connectionQueue_, false, null);
        }

        //clear automated star rating session values because now we have a new user
        _cly.moduleRatings.clearAutomaticStarRatingSessionCountInternal(_cly.connectionQueue_.getCountlyStore());
    }

    /**
     * Changes current device id to the one specified in parameter. Merges user profile with new id
     * (if any) with old profile.
     * @param deviceId new device id
     */
    void changeDeviceIdWithMerge(String deviceId) {
        if (deviceId == null || "".equals(deviceId)) {
            throw new IllegalStateException("deviceId cannot be null or empty");
        }

        if(!_cly.anyConsentGiven()){
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "Can't change Device ID if no consent is given");
            }
            return;
        }

        if(_cly.connectionQueue_.getDeviceId().temporaryIdModeEnabled() || _cly.connectionQueue_.queueContainsTemporaryIdItems()){
            //if we are in temporary ID mode or
            //at some moment have enabled temporary mode

            if(deviceId.equals(DeviceId.temporaryCountlyDeviceId)){
                //if we want to enter temporary ID mode
                //just exit, nothing to do

                if (_cly.isLoggingEnabled()) {
                    Log.w(Countly.TAG, "[changeDeviceId] About to enter temporary ID mode when already in it");
                }

                return;
            }

            // if a developer supplied ID is provided
            //we just exit this mode and set the id to the provided one
            exitTemporaryIdMode(DeviceId.Type.DEVELOPER_SUPPLIED, deviceId);
        } else {
            //we are not in temporary mode, nothing special happens
            // we are either making a simple ID change or entering temporary mode
            // in both cases we act the same as the temporary ID requests will be updated with the final ID later

            _cly.connectionQueue_.changeDeviceId(deviceId, _cly.moduleSessions.roundedSecondsSinceLastSessionDurationUpdate());

            //update remote config_ values if automatic update is enabled
            _cly.remoteConfigClearValues();
            if (_cly.remoteConfigAutomaticUpdateEnabled && _cly.anyConsentGiven()) {
                //request should be delayed, because of the delayed server merge
                _cly.moduleRemoteConfig.updateRemoteConfigValues(null, null, _cly.connectionQueue_, true, null);
            }
        }
    }

    @Override
    public void halt() {

    }
}
