package ly.count.android.sdk;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        Countly.CountlyFeatureNames.feedback,
        Countly.CountlyFeatureNames.clicks,
        Countly.CountlyFeatureNames.scrolls
    };

    protected boolean requiresConsent = false;

    final Map<String, Boolean> featureConsentValues = new HashMap<>();
    private final Map<String, String[]> groupedFeatures = new HashMap<>();
    String collectedConsentChanges;

    ModuleConsent(@NonNull final Countly cly, @NonNull final CountlyConfig config) {
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

    public boolean getConsent(@NonNull final String featureName) {
        return getConsentInternal(featureName);
    }

    public boolean anyConsentGiven() {
        if (!requiresConsent) {
            //no consent required - all consent given
            return true;
        }

        for (String key : featureConsentValues.keySet()) {
            if (getConsentTrue(key)) {
                return true;
            }
        }
        return false;
    }

    boolean getConsentInternal(@Nullable final String featureName) {
        if(featureName == null) {
            L.e("[ModuleConsent] getConsentInternal, Can't call this with a 'null' feature name!");
            return false;
        }

        boolean returnValue = getConsentSilent(featureName);
        L.v("[ModuleConsent] Returning consent for feature named: [" + featureName + "] [" + returnValue + "]");
        return returnValue;
    }

    /**
     * Should be used when not log message should be produced
     * @param featureName
     * @return
     */
    private boolean getConsentSilent(@NonNull final String featureName) {
        if (!requiresConsent) {
            //return true silently
            return true;
        }

        return getConsentTrue(featureName);
    }

    /**
     * Returns the true internally set value
     * @param featureName
     * @return
     */
    private boolean getConsentTrue(@NonNull final String featureName) {
        Boolean returnValue = featureConsentValues.get(featureName);

        if (returnValue == null) {
            returnValue = false;
        }

        return returnValue;
    }

    /**
     * Print the consent values of all features
     *
     * @return Returns link to Countly for call chaining
     */
    public void checkAllConsentInternal() {
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
    void doPushConsentSpecialAction(final boolean consentValue) {
        L.d("[ModuleConsent] Doing push consent special action: [" + consentValue + "]");
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
    private boolean isValidFeatureName(@Nullable final String name) {
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
    private @NonNull String formatConsentChanges(@NonNull final String[] features, final boolean consentValue) {
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

    void setConsentInternal(@Nullable final String[] featureNames, final boolean isConsentGiven) {
        final boolean isInit = _cly.isInitialized();//is the SDK initialized

        if (!requiresConsent) {
            //if consent is not required, ignore all calls to it
            return;
        }

        if (featureNames == null) {
            L.w("[ModuleConsent] Calling setConsent with null featureNames!");
            return;
        }

        String formattedChanges = formatConsentChanges(featureNames, isConsentGiven);

        if(!isInit) {
            //if SDK is not initialized then just set the values and send them at the end
            for (String featureName : featureNames) {
                L.d("[ModuleConsent] Setting consent for feature: [" + featureName + "] with value: [" + isConsentGiven + "]");

                if (!isValidFeatureName(featureName)) {
                    L.w("[ModuleConsent] Given feature: [" + featureName + "] is not a valid name, ignoring it");
                    continue;
                }

                featureConsentValues.put(featureName, isConsentGiven);
            }

            collectedConsentChanges = formattedChanges;

            return;
        }

        List<String> consentThatWillChange = new ArrayList<>(featureNames.length);

        for (String featureName : featureNames) {
            L.d("[ModuleConsent] Setting consent for feature: [" + featureName + "] with value: [" + isConsentGiven + "]");

            if (!isValidFeatureName(featureName)) {
                L.w("[ModuleConsent] Given feature: [" + featureName + "] is not a valid name, ignoring it");
                continue;
            }

            if(getConsentSilent(featureName) != isConsentGiven) {
                //if the current consent does not match the one give, add it to the list
                consentThatWillChange.add(featureName);

                //set new consent value
                featureConsentValues.put(featureName, isConsentGiven);
            }
        }

        for(String featureName : consentThatWillChange) {
            //special actions for each feature
            switch (featureName) {
                case Countly.CountlyFeatureNames.push:
                    doPushConsentSpecialAction(isConsentGiven);
                    break;
                case Countly.CountlyFeatureNames.sessions:
                    if(isConsentGiven) {
                        //if consent was just given and manual sessions sessions are not enabled, start a session
                        if (!_cly.moduleSessions.manualSessionControlEnabled) {
                            _cly.moduleSessions.beginSessionInternal();
                        }
                    } else {
                        if (!_cly.isBeginSessionSent) {
                            //if session consent was removed and first begins session was not sent
                            //that means that we might not have sent the initially given location information

                            _cly.moduleLocation.sendCurrentLocationIfValid();
                        }
                    }

                    break;
                case Countly.CountlyFeatureNames.location:
                    if (!isConsentGiven) {
                        //if consent is about to be removed
                        doLocationConsentSpecialErasure();
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

        //if countly is initialized and collected changes are already sent, send consent now
        requestQueueProvider.sendConsentChanges(formattedChanges);

        _cly.context_.sendBroadcast(new Intent(Countly.CONSENT_BROADCAST));
    }

    /**
     * Remove the consent of a feature
     *
     * @param featureNames the names of features for which consent should be removed
     * @return Returns link to Countly for call chaining
     */
    public void removeConsentInternal(@Nullable final String[] featureNames) {
        L.d("[ModuleConsent] Removing consent for features named: [" + Arrays.toString(featureNames) + "]");

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
    public void removeConsentAllInternal() {
        L.d("[ModuleConsent] Removing consent for all features");

        removeConsentInternal(validFeatureNames);
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        if (requiresConsent) {
            //do appropriate action regarding the current consent state

            //remove persistent push flag if no push consent was set
            doPushConsentSpecialAction(getConsentSilent(Countly.CountlyFeatureNames.push));

            //do delayed location erasure, if needed consent was not given during init
            if(!getConsentSilent(Countly.CountlyFeatureNames.location)) {
                doLocationConsentSpecialErasure();
            }

            //send collected consent changes that were made before initialization
            if(collectedConsentChanges != null) {
                requestQueueProvider.sendConsentChanges(collectedConsentChanges);
                collectedConsentChanges = null;
            }

            _cly.context_.sendBroadcast(new Intent(Countly.CONSENT_BROADCAST));

            if (L.logEnabled()) {
                L.d("[ModuleConsent] [Init] Countly is initialized with the current consent state:");
                checkAllConsentInternal();
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

                checkAllConsentInternal();
            }
        }

        /**
         * Get the current consent state of a feature
         *
         * @param featureName the name of a feature for which consent should be checked
         * @return the consent value
         */
        public boolean getConsent(@Nullable final String featureName) {
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
                removeConsentAllInternal();
            }
        }

        /**
         * Remove the consent of a feature
         *
         * @param featureNames the names of features for which consent should be removed
         * @return Returns link to Countly for call chaining
         */
        public void removeConsent(@Nullable final String[] featureNames) {
            synchronized (_cly) {
                removeConsentInternal(featureNames);
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
        public void giveConsent(@Nullable final String[] featureNames) {
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
        public void setConsent(@Nullable final String[] featureNames, final boolean isConsentGiven) {
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
        public void setConsentFeatureGroup(@Nullable final String groupName, final boolean isConsentGiven) {
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
        public void createFeatureGroup(@Nullable final String groupName, @Nullable final String[] features) {
            synchronized (_cly) {
                groupedFeatures.put(groupName, features);
            }
        }
    }
}
