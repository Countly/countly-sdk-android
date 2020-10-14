package ly.count.android.sdk;

import android.util.Log;

public class ModuleLocation extends ModuleBase {

    boolean locationDisabled = false;
    String locationCountryCode = null;
    String locationCity = null;
    String locationGpsCoordinates = null;
    String locationIpAddress = null;

    Location locationInterface = null;

    ModuleLocation(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleLocation] Initialising");
        }

        //do location related things
        if (config.disableLocation) {
            locationDisabled = true;
            disableLocationInternal();
        } else {
            //if we are not disabling location, check for other set values
            if (config.locationIpAddress != null || config.locationLocation != null || config.locationCity != null || config.locationCountyCode != null) {
                setLocationInternal(config.locationCountyCode, config.locationCity, config.locationLocation, config.locationIpAddress);
            }
        }

        locationInterface = new ModuleLocation.Location();
    }

    void resetLocationValues() {
        locationCity = null;
        locationCountryCode = null;
        locationGpsCoordinates = null;
        locationIpAddress = null;
    }

    boolean anyValidLocation() {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleLocation] Calling 'anyValidLocation'");
        }

        if (locationDisabled) {
            return false;
        }

        if(locationCountryCode != null || locationCity != null || locationIpAddress != null || locationGpsCoordinates != null) {
            return true;
        }

        return false;
    }

    void sendCurrentLocation() {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleLocation] Calling 'sendCurrentLocation'");
        }
        _cly.connectionQueue_.sendLocation(locationDisabled, locationCountryCode, locationCity, locationGpsCoordinates, locationIpAddress);
    }

    void disableLocationInternal() {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleLocation] Calling 'disableLocationInternal'");
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.location)) {
            //can't send disable location request if no consent given
            return;
        }

        resetLocationValues();
        locationDisabled = true;
        _cly.connectionQueue_.sendLocation(true, null, null, null, null);
    }

    void setLocationInternal(String country_code, String city, String gpsCoordinates, String ipAddress) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleLocation] Calling 'setLocationInternal'");
        }

        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleLocation] Setting location parameters, cc[" + country_code + "] cy[" + city + "] gps[" + gpsCoordinates + "] ip[" + ipAddress + "]");
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.location)) {
            return;
        }

        locationCountryCode = country_code;
        locationCity = city;
        locationGpsCoordinates = gpsCoordinates;
        locationIpAddress = ipAddress;

        if ((country_code == null && city != null) || (city == null && country_code != null)) {
            if (_cly.isLoggingEnabled()) {
                Log.w(Countly.TAG, "[ModuleLocation] In \"setLocation\" both city and country code need to be set at the same time to be sent");
            }
        }

        if (country_code != null || city != null || gpsCoordinates != null || ipAddress != null) {
            locationDisabled = false;
        }

        if (_cly.isBeginSessionSent || !Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.sessions)) {
            //send as a separate request if either begin session was already send and we missed our first opportunity
            //or if consent for sessions is not given and our only option to send this is as a separate request
            _cly.connectionQueue_.sendLocation(locationDisabled, locationCountryCode, locationCity, locationGpsCoordinates, locationIpAddress);
        } else {
            //will be sent a part of begin session
        }

        return;
    }

    @Override
    void initFinished(CountlyConfig config) {

    }

    @Override
    void halt() {
        locationInterface = null;
    }

    public class Location {
        /**
         * Disable sending of location data. Erases server side saved location information
         */
        public void disableLocation() {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Location] Calling 'disableLocation'");
                }

                disableLocationInternal();
            }
        }

        /**
         * Set location parameters. If they are set before begin_session, they will be sent as part of it.
         * If they are set after, then they will be sent as a separate request.
         * If this is called after disabling location, it will enable it.
         *
         * @param countryCode ISO Country code for the user's country
         * @param city Name of the user's city
         * @param gpsCoordinates comma separate lat and lng values. For example, "56.42345,123.45325"
         * @param ipAddress ipAddress like "192.168.88.33"
         * @return Returns link to Countly for call chaining
         */
        public void setLocation(String countryCode, String city, String gpsCoordinates, String ipAddress) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Location] Calling 'setLocation'");
                }

                setLocationInternal(countryCode, city, gpsCoordinates, ipAddress);
            }
        }
    }
}
