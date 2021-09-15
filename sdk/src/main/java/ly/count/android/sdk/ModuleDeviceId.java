package ly.count.android.sdk;

class ModuleDeviceId extends ModuleBase implements OpenUDIDProvider{
    boolean exitTempIdAfterInit = false;

    ModuleLog L;

    ModuleDeviceId(Countly cly, CountlyConfig config) {
        super(cly, config);

        L = cly.L;
        L.v("[ModuleDeviceId] Initialising");

        boolean customIDWasProvided = (config.deviceID != null);
        if (config.temporaryDeviceIdEnabled && !customIDWasProvided) {
            //if we want to use temporary ID mode and no developer custom ID is provided
            //then we override that custom ID to set the temporary mode
            config.deviceID = DeviceId.temporaryCountlyDeviceId;
        }

        //choose what kind of device ID will be used
        if (config.deviceID != null) {
            //if the developer provided a ID
            //or it's a temporary ID
            config.deviceIdInstance = new DeviceId(config.storageProvider, config.deviceID, L, this);
        } else {
            //the dev provided only a type and the SDK should generate a appropriate ID
            config.deviceIdInstance = new DeviceId(config.storageProvider, config.idMode, L, this);
        }

        //initialise the set device ID value
        config.deviceIdInstance.init(config.context);

        boolean temporaryDeviceIdIsCurrentlyEnabled = config.deviceIdInstance.temporaryIdModeEnabled();
        L.d("[ModuleDeviceId] [TemporaryDeviceId] Temp ID should be enabled[" + config.temporaryDeviceIdEnabled + "] Currently enabled: [" + temporaryDeviceIdIsCurrentlyEnabled + "]");

        if (temporaryDeviceIdIsCurrentlyEnabled && customIDWasProvided) {
            //if a custom ID was provided and we are still in temporary ID mode
            //it means the we had tempID mode at the previous app end
            //exit tempID after init finished
            L.d("[ModuleDeviceId] [TemporaryDeviceId] Decided we have to exit temporary device ID mode, mode enabled: [" + config.temporaryDeviceIdEnabled + "], custom Device ID Set: [" + customIDWasProvided + "]");

            exitTempIdAfterInit = true;
        }
    }

    void exitTemporaryIdMode(DeviceId.Type type, String deviceId) {
        L.d("[ModuleDeviceId] Calling exitTemporaryIdMode");

        if (!_cly.isInitialized()) {
            throw new IllegalStateException("init must be called before exitTemporaryIdMode");
        }

        //start by changing stored ID
        _cly.connectionQueue_.getDeviceId().changeToId(_cly.context_, type, deviceId, true);//run init because not clear if types other then dev supplied can be provided

        //update stored request for ID change to use this new ID
        String[] storedRequests = storageProvider.getRequests();
        String temporaryIdTag = "&device_id=" + DeviceId.temporaryCountlyDeviceId;
        String newIdTag = "&device_id=" + deviceId;

        boolean foundOne = false;
        for (int a = 0; a < storedRequests.length; a++) {
            if (storedRequests[a].contains(temporaryIdTag)) {
                L.d("[ModuleDeviceId] [exitTemporaryIdMode] Found a tag to replace in: [" + storedRequests[a] + "]");
                storedRequests[a] = storedRequests[a].replace(temporaryIdTag, newIdTag);
                foundOne = true;
            }
        }

        if (foundOne) {
            storageProvider.replaceRequests(storedRequests);
        }

        //update remote config_ values if automatic update is enabled
        _cly.remoteConfigClearValues();
        if (_cly.remoteConfigAutomaticUpdateEnabled && consentProvider.anyConsentGiven()) {
            _cly.moduleRemoteConfig.updateRemoteConfigValues(null, null, _cly.connectionQueue_, false, null);
        }

        _cly.doStoredRequests();
    }

    /**
     * Changes current device id type to the one specified in parameter. Closes current session and
     * reopens new one with new id. Doesn't merge user profiles on the server
     *
     * @param type Device ID type to change to
     * @param deviceId Optional device ID for a case when type = DEVELOPER_SPECIFIED
     */
    void changeDeviceIdWithoutMerge(DeviceId.Type type, String deviceId) {
        if (type == null) {
            L.e("[ModuleDeviceId] changeDeviceIdWithoutMerge, type cannot be null");
            return;
        }

        if (type == DeviceId.Type.DEVELOPER_SUPPLIED && deviceId == null) {
            L.e("[ModuleDeviceId] changeDeviceIdWithoutMerge, When type is 'DEVELOPER_SUPPLIED', provided deviceId cannot be null");
            return;
        }

        DeviceId currentDeviceId = _cly.connectionQueue_.getDeviceId();

        if (currentDeviceId.temporaryIdModeEnabled() && (deviceId != null && deviceId.equals(DeviceId.temporaryCountlyDeviceId))) {
            // we already are in temporary mode and we want to set temporary mode
            // in this case we just ignore the request since nothing has to be done
            return;
        }

        if (currentDeviceId.temporaryIdModeEnabled() || _cly.connectionQueue_.queueContainsTemporaryIdItems()) {
            // we are about to exit temporary ID mode
            // because of the previous check, we know that the new type is a different one
            // we just call our method for exiting it
            // we don't end the session, we just update the device ID and connection queue
            exitTemporaryIdMode(type, deviceId);
        }

        // we are either making a simple ID change or entering temporary mode
        // in both cases we act the same as the temporary ID requests will be updated with the final ID later

        //force flush events so that they are associated correctly
        _cly.sendEventsIfNeeded(true);

        //update remote config_ values after id change if automatic update is enabled
        _cly.moduleRemoteConfig.clearAndDownloadAfterIdChange();

        _cly.moduleSessions.endSessionInternal(currentDeviceId.getId());
        currentDeviceId.changeToId(_cly.context_, type, deviceId, true);
        _cly.moduleSessions.beginSessionInternal();

        //clear automated star rating session values because now we have a new user
        _cly.moduleRatings.clearAutomaticStarRatingSessionCountInternal(_cly.connectionQueue_.getCountlyStore());
    }

    /**
     * Changes current device id to the one specified in parameter. Merges user profile with new id
     * (if any) with old profile.
     *
     * @param deviceId new device id
     */
    void changeDeviceIdWithMerge(String deviceId) {
        if (deviceId == null || "".equals(deviceId)) {
            throw new IllegalStateException("deviceId cannot be null or empty");
        }

        if (_cly.connectionQueue_.getDeviceId().temporaryIdModeEnabled() || _cly.connectionQueue_.queueContainsTemporaryIdItems()) {
            //if we are in temporary ID mode or
            //at some moment have enabled temporary mode

            if (deviceId.equals(DeviceId.temporaryCountlyDeviceId)) {
                //if we want to enter temporary ID mode
                //just exit, nothing to do

                L.w("[ModuleDeviceId, changeDeviceId] About to enter temporary ID mode when already in it");

                return;
            }

            // if a developer supplied ID is provided
            //we just exit this mode and set the id to the provided one
            exitTemporaryIdMode(DeviceId.Type.DEVELOPER_SUPPLIED, deviceId);
        } else {
            //we are not in temporary mode, nothing special happens
            // we are either making a simple ID change or entering temporary mode
            // in both cases we act the same as the temporary ID requests will be updated with the final ID later

            //update remote config_ values after id change if automatic update is enabled
            _cly.moduleRemoteConfig.clearAndDownloadAfterIdChange();

            _cly.connectionQueue_.changeDeviceId(deviceId, _cly.moduleSessions.roundedSecondsSinceLastSessionDurationUpdate());
        }
    }

    @Override
    public void initFinished(CountlyConfig config) {
        if (exitTempIdAfterInit) {
            L.i("[ModuleDeviceId, initFinished] Exiting temp ID at the end of init");
            exitTemporaryIdMode(DeviceId.Type.DEVELOPER_SUPPLIED, config.deviceID);
        }
    }

    @Override
    void halt() {

    }

    @Override public String getOpenUDID() {
        OpenUDIDAdapter.sync(_cly.context_);
        return OpenUDIDAdapter.OpenUDID;
    }
}
