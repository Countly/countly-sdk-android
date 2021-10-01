package ly.count.android.sdk;

import android.content.Intent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleConsent extends ModuleBase implements ConsentProvider {
    Consent consentInterface = null;

    //a list of valid feature names that are used for checking
    protected static final String[] validFeatureNames = new String[] {
        Countly.CountlyFeatureNames.sessions,
        Countly.CountlyFeatureNames.events,
        Countly.CountlyFeatureNames.views,
        Countly.CountlyFeatureNames.location,
        Countly.CountlyFeatureNames.crashes,
        Countly.CountlyFeatureNames.attribution,
        Countly.CountlyFeatureNames.users,
        Countly.CountlyFeatureNames.push,
        Countly.CountlyFeatureNames.starRating,
        Countly.CountlyFeatureNames.remoteConfig,
        Countly.CountlyFeatureNames.apm,
        Countly.CountlyFeatureNames.feedback
    };

    protected boolean requiresConsent = false;

    final Map<String, Boolean> featureConsentValues = new HashMap<>();
    private final Map<String, String[]> groupedFeatures = new HashMap<>();
    final List<String> collectedConsentChanges = new ArrayList<>();

    Boolean delayedPushConsent = null;//if this is set, consent for push has to be set before finishing init and sending push changes
    boolean delayedLocationErasure = false;//if location needs to be cleared at the end of init

    ModuleConsent(Countly cly, CountlyConfig config) {
        super(cly, config);
        consentProvider = this;
        config.consentProvider = this;
        L.v("[ModuleConsent] Initialising");
        L.i("[ModuleConsent] Is consent required? [" + config.shouldRequireConsent + "]");

        //react to given consent
        if (config.shouldRequireConsent) {
            requiresConsent = config.shouldRequireConsent;
            if (config.enabledFeatureNames == null) {
                L.i("[Init] Consent has been required but no consent was given during init");
            } else {
                setConsentInternal(config.enabledFeatureNames, true);
            }
        }

        consentInterface = new Consent();
    }

    public boolean getConsent(String featureName) {
        return getConsentInternal(featureName);
    }

    public boolean anyConsentGiven() {
        if (!requiresConsent) {
            //no consent required - all consent given
            return true;
        }

        for (String key : featureConsentValues.keySet()) {
            if (featureConsentValues.get(key)) {
                return true;
            }
        }
        return false;
    }

    boolean getConsentInternal(String featureName) {
        if (!requiresConsent) {
            //return true silently
            return true;
        }

        Boolean returnValue = featureConsentValues.get(featureName);

        if (returnValue == null) {
            returnValue = false;
        }

        L.v("[ModuleConsent] Returning consent for feature named: [" + featureName + "] [" + returnValue + "]");

        return returnValue;
    }

    /**
     * Print the consent values of all features
     *
     * @return Returns link to Countly for call chaining
     */
    public void checkAllConsent() {
        L.d("[ModuleConsent] Checking and printing consent for All features");
        L.d("[ModuleConsent] Is consent required? [" + requiresConsent + "]");

        //make sure push consent has been added to the feature map
        getConsent(Countly.CountlyFeatureNames.push);

        StringBuilder sb = new StringBuilder();

        for (String key : featureConsentValues.keySet()) {
            sb.append("Feature named [").append(key).append("], consent value: [").append(featureConsentValues.get(key)).append("]\n");
        }

        L.d(sb.toString());
    }

    /**
     * Special things needed to be done during setting push consent
     *
     * @param consentValue The value of push consent
     */
    void doPushConsentSpecialAction(boolean consentValue) {
        L.d("[Countly] Doing push consent special action: [" + consentValue + "]");
        _cly.countlyStore.setConsentPush(consentValue);
    }

    /**
     * Actions needed to be done for the consent related location erasure
     */
    void doLocationConsentSpecialErasure() {
        _cly.moduleLocation.resetLocationValues();
        requestQueueProvider.sendLocation(true, null, null, null, null);
    }

    /**
     * Check if the given name is a valid feature name
     *
     * @param name the name of the feature to be tested if it is valid
     * @return returns true if value is contained in feature name array
     */
    private boolean isValidFeatureName(String name) {
        for (String fName : validFeatureNames) {
            if (fName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prepare features into json format
     *
     * @param features the names of features that are about to be changed
     * @param consentValue the value for the new consent
     * @return provided consent changes in json format
     */
    private String formatConsentChanges(String[] features, boolean consentValue) {
        StringBuilder preparedConsent = new StringBuilder();
        preparedConsent.append("{");

        for (int a = 0; a < features.length; a++) {
            if (a != 0) {
                preparedConsent.append(",");
            }
            preparedConsent.append('"');
            preparedConsent.append(features[a]);
            preparedConsent.append('"');
            preparedConsent.append(':');
            preparedConsent.append(consentValue);
        }

        preparedConsent.append("}");

        return preparedConsent.toString();
    }

    void setConsentInternal(String[] featureNames, boolean isConsentGiven) {
        final boolean isInit = _cly.isInitialized();//is the SDK initialized

        if (!requiresConsent) {
            //if consent is not required, ignore all calls to it
            return;
        }

        if (featureNames == null) {
            L.w("[Countly] Calling setConsent with null featureNames!");
            return;
        }

        boolean previousSessionsConsent = false;
        if (featureConsentValues.containsKey(Countly.CountlyFeatureNames.sessions)) {
            previousSessionsConsent = featureConsentValues.get(Countly.CountlyFeatureNames.sessions);
        }

        boolean previousLocationConsent = false;
        if (featureConsentValues.containsKey(Countly.CountlyFeatureNames.location)) {
            previousLocationConsent = featureConsentValues.get(Countly.CountlyFeatureNames.location);
        }

        boolean currentSessionConsent = previousSessionsConsent;

        for (String featureName : featureNames) {
            L.d("[Countly] Setting consent for feature: [" + featureName + "] with value: [" + isConsentGiven + "]");

            if (!isValidFeatureName(featureName)) {
                L.w("[Countly] Given feature: [" + featureName + "] is not a valid name, ignoring it");
                continue;
            }

            featureConsentValues.put(featureName, isConsentGiven);

            //special actions for each feature
            switch (featureName) {
                case Countly.CountlyFeatureNames.push:
                    if (isInit) {
                        //if the SDK is already initialized, do the special action now
                        doPushConsentSpecialAction(isConsentGiven);
                    } else {
                        //do the special action later
                        delayedPushConsent = isConsentGiven;
                    }
                    break;
                case Countly.CountlyFeatureNames.sessions:
                    currentSessionConsent = isConsentGiven;
                    break;
                case Countly.CountlyFeatureNames.location:
                    if (previousLocationConsent && !isConsentGiven) {
                        //if consent is about to be removed
                        if (isInit) {
                            doLocationConsentSpecialErasure();
                        } else {
                            delayedLocationErasure = true;
                        }
                    }
                    break;
                case Countly.CountlyFeatureNames.apm:
                    if (!isConsentGiven) {
                        //in case APM consent is removed, clear custom and network traces
                        _cly.moduleAPM.clearNetworkTraces();
                        _cly.moduleAPM.cancelAllTracesInternal();
                    }
            }
        }

        String formattedChanges = formatConsentChanges(featureNames, isConsentGiven);

        if (isInit && (collectedConsentChanges.size() == 0)) {
            //if countly is initialized and collected changes are already sent, send consent now
            requestQueueProvider.sendConsentChanges(formattedChanges);

            _cly.context_.sendBroadcast(new Intent(Countly.CONSENT_BROADCAST));

            //if consent has changed and it was set to true
            if ((previousSessionsConsent != currentSessionConsent) && currentSessionConsent) {
                //if consent was given, we need to begin the session
                if (_cly.isBeginSessionSent) {
                    //if the first timing for a beginSession call was missed, send it again
                    if (!_cly.moduleSessions.manualSessionControlEnabled) {
                        _cly.moduleSessions.beginSessionInternal();
                    }
                }
            }

            //if consent was changed and set to false
            if ((previousSessionsConsent != currentSessionConsent) && !currentSessionConsent) {
                if (!_cly.isBeginSessionSent) {
                    //if session consent was removed and first begins session was not sent
                    //that means that we might not have sent the initially given location information

                    if (_cly.moduleLocation.anyValidLocation()) {
                        _cly.moduleLocation.sendCurrentLocation();
                    }
                }
            }
        } else {
            // if countly is not initialized, collect and send it after it is

            collectedConsentChanges.add(formattedChanges);
        }
    }

    /**
     * Remove the consent of a feature
     *
     * @param featureNames the names of features for which consent should be removed
     * @return Returns link to Countly for call chaining
     */
    public void removeConsent(String[] featureNames) {
        L.d("[Countly] Removing consent for features named: [" + Arrays.toString(featureNames) + "]");

        if (!_cly.isInitialized()) {
            L.w("Calling 'removeConsent' before initialising the SDK is deprecated!");
        }

        setConsentInternal(featureNames, false);
    }

    /**
     * Remove consent for all features
     *
     * @return Returns link to Countly for call chaining
     */
    public void removeConsentAll() {
        L.d("[Countly] Removing consent for all features");

        removeConsent(validFeatureNames);
    }

    @Override
    void initFinished(CountlyConfig config) {
        if (requiresConsent) {
            //do delayed push consent action, if needed
            if (delayedPushConsent != null) {
                doPushConsentSpecialAction(delayedPushConsent);
            }

            //remove persistent push flag if no push consent was set
            if (!featureConsentValues.containsKey(Countly.CountlyFeatureNames.push)) {
                doPushConsentSpecialAction(false);
            }

            //do delayed location erasure, if needed
            if (delayedLocationErasure) {
                doLocationConsentSpecialErasure();
            }

            //send collected consent changes that were made before initialization
            if (collectedConsentChanges.size() != 0) {
                for (String changeItem : collectedConsentChanges) {
                    requestQueueProvider.sendConsentChanges(changeItem);
                }
                collectedConsentChanges.clear();
            }

            _cly.context_.sendBroadcast(new Intent(Countly.CONSENT_BROADCAST));

            if (L.logEnabled()) {
                L.d("[ModuleConsent] [Init] Countly is initialized with the current consent state:");
                checkAllConsent();
            }
        }
    }

    @Override
    void halt() {
        consentInterface = null;
    }

    public class Consent {
        /**
         * Print the consent values of all features
         *
         * @return Returns link to Countly for call chaining
         */
        public void checkAllConsent() {
            synchronized (_cly) {
                L.i("[Consent] calling checkAllConsent");

                checkAllConsent();
            }
        }

        /**
         * Get the current consent state of a feature
         *
         * @param featureName the name of a feature for which consent should be checked
         * @return the consent value
         */
        public boolean getConsent(String featureName) {
            synchronized (_cly) {
                return getConsentInternal(featureName);
            }
        }

        /**
         * Remove consent for all features
         *
         * @return Returns link to Countly for call chaining
         */
        public void removeConsentAll() {
            synchronized (_cly) {
                ModuleConsent.this.removeConsentAll();
            }
        }

        /**
         * Remove the consent of a feature
         *
         * @param featureNames the names of features for which consent should be removed
         * @return Returns link to Countly for call chaining
         */
        public void removeConsent(String[] featureNames) {
            synchronized (_cly) {
                removeConsent(featureNames);
            }
        }

        /**
         * Gives consent for all features
         *
         * @return Returns link to Countly for call chaining
         */
        public void giveConsentAll() {
            synchronized (_cly) {
                L.i("[Consent] Giving consent for all features");

                if (!_cly.isInitialized()) {
                    L.w("[Consent] Calling this before initialising the SDK is deprecated!");
                }

                setConsentInternal(validFeatureNames, true);
            }
        }

        /**
         * Give the consent to a feature
         *
         * @param featureNames the names of features for which consent should be given
         * @return Returns link to Countly for call chaining
         */
        public void giveConsent(String[] featureNames) {
            synchronized (_cly) {
                setConsentInternal(featureNames, true);
            }
        }

        /**
         * Set the consent of a feature
         *
         * @param featureNames feature names for which consent should be changed
         * @param isConsentGiven the consent value that should be set
         * @return Returns link to Countly for call chaining
         */
        public void setConsent(String[] featureNames, boolean isConsentGiven) {
            synchronized (_cly) {
                setConsentInternal(featureNames, isConsentGiven);
            }
        }

        /**
         * Set the consent of a feature group
         *
         * @param groupName name of the consent group
         * @param isConsentGiven the value that should be set for this consent group
         * @return Returns link to Countly for call chaining
         */
        public void setConsentFeatureGroup(String groupName, boolean isConsentGiven) {
            synchronized (_cly) {
                if (!groupedFeatures.containsKey(groupName)) {
                    L.d("[Countly] Trying to set consent for a unknown feature group: [" + groupName + "]");

                    return;
                }

                setConsentInternal(groupedFeatures.get(groupName), isConsentGiven);
            }
        }

        /**
         * Group multiple features into a feature group
         *
         * @param groupName name of the consent group
         * @param features array of feature to be added to the consent group
         * @return Returns link to Countly for call chaining
         */
        public void createFeatureGroup(String groupName, String[] features) {
            synchronized (_cly) {
                groupedFeatures.put(groupName, features);
            }
        }
    }
}
