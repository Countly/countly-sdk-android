package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

public class ModuleLocation extends ModuleBase {

    boolean locationDisabled = false;
    String locationCountryCode = null;
    String locationCity = null;
    String locationGpsCoordinates = null;
    String locationIpAddress = null;

    Location locationInterface = null;

    ModuleLocation(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleLocation] Initialising");

        locationInterface = new Location();
    }

    void resetLocationValues() {
        locationCity = null;
        locationCountryCode = null;
        locationGpsCoordinates = null;
        locationIpAddress = null;
    }

    void sendCurrentLocationIfValid() {
        L.d("[ModuleLocation] Calling 'sendCurrentLocationIfValid'");

        if (locationDisabled) {
            return;
        }

        if (locationCountryCode != null || locationCity != null || locationIpAddress != null || locationGpsCoordinates != null) {
            requestQueueProvider.sendLocation(locationDisabled, locationCountryCode, locationCity, locationGpsCoordinates, locationIpAddress);
        }
    }

    void disableLocationInternal() {
        L.d("[ModuleLocation] Calling 'disableLocationInternal'");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.location)) {
            //can't send disable location request if no consent given
            return;
        }

        locationDisabled = true;
        performLocationErasure();
    }

    void performLocationErasure() {
        resetLocationValues();
        requestQueueProvider.sendLocation(true, null, null, null, null);
    }

    void setLocationInternal(@Nullable String country_code, @Nullable String city, @Nullable String gpsCoordinates, @Nullable String ipAddress) {
        L.d("[ModuleLocation] Calling 'setLocationInternal'");

        L.d("[ModuleLocation] Setting location parameters, cc[" + country_code + "] cy[" + city + "] gps[" + gpsCoordinates + "] ip[" + ipAddress + "]");

        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.location)) {
            return;
        }

        locationCountryCode = country_code;
        locationCity = city;
        locationGpsCoordinates = gpsCoordinates;
        locationIpAddress = ipAddress;

        if ((country_code == null && city != null) || (city == null && country_code != null)) {
            L.w("[ModuleLocation] In \"setLocation\" both city and country code need to be set at the same time to be sent");
        }

        if (country_code != null || city != null || gpsCoordinates != null || ipAddress != null) {
            locationDisabled = false;
        }

        if (_cly.isBeginSessionSent || !consentProvider.getConsent(Countly.CountlyFeatureNames.sessions)) {
            //send as a separate request if either begin session was already send and we missed our first opportunity
            //or if consent for sessions is not given and our only option to send this is as a separate request
            requestQueueProvider.sendLocation(locationDisabled, locationCountryCode, locationCity, locationGpsCoordinates, locationIpAddress);
        } else {
            //will be sent a part of begin session
        }
    }

    @Override
    void initFinished(CountlyConfig config) {
        //check first if consent is even given
        if (!consentProvider.getConsent(Countly.CountlyFeatureNames.location)) {
            //if no consent is given, perform location erasure
            performLocationErasure();
        } else {
            //if consent is given, check if location isn't disabled
            if (config.disableLocation) {
                //disable location if needed
                disableLocationInternal();
            } else {
                //if we are not disabling location, check for other set values
                if (config.locationIpAddress != null || config.locationLocation != null || config.locationCity != null || config.locationCountyCode != null) {
                    setLocationInternal(config.locationCountyCode, config.locationCity, config.locationLocation, config.locationIpAddress);
                }
            }
        }
    }

    @Override
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
        if(consentChangeDelta.contains(Countly.CountlyFeatureNames.location)) {
            if (!newConsent) {
                //if consent is about to be removed
                performLocationErasure();
            }
        }
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
                L.i("[Location] Calling 'disableLocation'");

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
        public void setLocation(@Nullable String countryCode, @Nullable String city, @Nullable String gpsCoordinates, @Nullable String ipAddress) {
            synchronized (_cly) {
                L.i("[Location] Calling 'setLocation'");

                setLocationInternal(countryCode, city, gpsCoordinates, ipAddress);
            }
        }
    }
}
