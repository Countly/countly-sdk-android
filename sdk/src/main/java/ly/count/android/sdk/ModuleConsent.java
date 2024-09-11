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
    protected static final String[] validFeatureNames = {
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
        Countly.CountlyFeatureNames.scrolls,
        Countly.CountlyFeatureNames.content,
    };

    public enum ConsentChangeSource {ChangeConsentCall, DeviceIDChangedNotMerged}

    protected boolean requiresConsent = false;

    final Map<String, Boolean> featureConsentValues = new HashMap<>();
    private final Map<String, String[]> groupedFeatures = new HashMap<>();

    ModuleConsent(@NonNull final Countly cly, @NonNull final CountlyConfig config) {
        super(cly, config);
        consentProvider = this;
        config.consentProvider = this;
        L.v("[ModuleConsent] constructor, Initialising");
        L.i("[ModuleConsent] Is consent required? [" + config.shouldRequireConsent + "]");

        //setup initial consent data structure
        //initialize all features to "false"
        for (String featureName : validFeatureNames) {
            featureConsentValues.put(featureName, false);
        }

        //react to given consent during init
        if (config.shouldRequireConsent) {
            requiresConsent = config.shouldRequireConsent;
            if (config.enabledFeatureNames == null && !config.enableAllConsents) {
                L.i("[ModuleConsent] constructor, Consent has been required but no consent was given during init");
            } else {

                if (config.enableAllConsents) {
                    //set all consent values to "true"
                    L.i("[ModuleConsent] constructor, Giving consent for all features");
                    for (String featureName : validFeatureNames) {
                        featureConsentValues.put(featureName, true);
                    }
                } else {
                    //set provided consent values
                    L.i("[ModuleConsent] constructor, Giving consent for features named: [" + Arrays.toString(config.enabledFeatureNames) + "]");
                    for (String providedFeature : config.enabledFeatureNames) {
                        featureConsentValues.put(providedFeature, true);
                    }
                }
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
        if (featureName == null) {
            L.e("[ModuleConsent] getConsentInternal, Can't call this with a 'null' feature name!");
            return false;
        }

        if (!requiresConsent) {
            //return true silently
            return true;
        }

        boolean returnValue = getConsentTrue(featureName);
        L.v("[ModuleConsent] getConsentInternal, Returning consent for feature named: [" + featureName + "] [" + returnValue + "]");
        return returnValue;
    }

    /**
     * Returns the true internally set value
     *
     * @param featureName
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
     */
    public void checkAllConsentInternal() {
        L.d("[ModuleConsent] checkAllConsentInternal, consentRequired: [" + requiresConsent + "]");
        //make sure push consent has been added to the feature map
        getConsent(Countly.CountlyFeatureNames.push);

        StringBuilder sb = new StringBuilder();

        for (String key : featureConsentValues.keySet()) {
            sb.append("Feature named [").append(key).append("], consent value: [").append(featureConsentValues.get(key)).append("]\n");
        }

        L.d("[ModuleConsent] checkAllConsentInternal, Current consent state: [" + sb + "]");
    }

    /**
     * Special things needed to be done during setting push consent
     *
     * @param consentValue The value of push consent
     */
    void doPushConsentSpecialAction(final boolean consentValue) {
        L.d("[ModuleConsent] doPushConsentSpecialAction, consentValue: [" + consentValue + "]");
        _cly.countlyStore.setConsentPush(consentValue);
        _cly.context_.sendBroadcast(new Intent(Countly.CONSENT_BROADCAST));
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
     * Prepare the current feature state into a json format
     *
     * @param features the names of features that are about to be changed
     * @return provided consent changes in json format
     */
    private @NonNull String formatConsentState(@NonNull final Map<String, Boolean> features) {
        StringBuilder preparedConsent = new StringBuilder();
        preparedConsent.append("{");

        boolean commaAdded = false;

        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            if (commaAdded) {
                preparedConsent.append(",");
            } else {
                commaAdded = true;
            }

            preparedConsent.append('"');
            preparedConsent.append(entry.getKey());
            preparedConsent.append('"');
            preparedConsent.append(':');
            preparedConsent.append(entry.getValue());
        }

        preparedConsent.append("}");

        return preparedConsent.toString();
    }

    void setConsentInternal(@Nullable final String[] featureNames, final boolean isConsentGiven, final ConsentChangeSource changeSource) {
        L.d("[ModuleConsent] setConsentInternal, featureNames: [" + Arrays.toString(featureNames) + "] to value: [" + isConsentGiven + "], changeSource: [" + changeSource + "]");
        if (!requiresConsent) {
            L.i("[ModuleConsent] setConsentInternal, Consent is not required, ignoring the call");
            return;
        }

        if (featureNames == null) {
            L.w("[ModuleConsent] setConsentInternal, Calling setConsent with null featureNames!");
            return;
        }

        List<String> consentThatWillChange = new ArrayList<>(featureNames.length);

        for (String featureName : featureNames) {
            if (!isValidFeatureName(featureName)) {
                L.w("[ModuleConsent] setConsentInternal, Given feature: [" + featureName + "] is not a valid name, ignoring it");
                continue;
            }

            if (getConsentTrue(featureName) != isConsentGiven) {
                //if the current consent does not match the one give, add it to the list
                consentThatWillChange.add(featureName);

                //set new consent value
                featureConsentValues.put(featureName, isConsentGiven);
            }
        }

        for (ModuleBase module : _cly.modules) {
            module.onConsentChanged(consentThatWillChange, isConsentGiven, changeSource);
        }

        if (isConsentGiven || !changeSource.equals(ConsentChangeSource.DeviceIDChangedNotMerged)) {
            //send consent changes
            String formattedConsentState = formatConsentState(featureConsentValues);
            L.v("[ModuleConsent] setConsentInternal, Sending consent changes: [" + formattedConsentState + "]");
            requestQueueProvider.sendConsentChanges(formattedConsentState);
        }
    }

    /**
     * Remove the consent of a feature
     *
     * @param featureNames the names of features for which consent should be removed
     */
    public void removeConsentInternal(@Nullable final String[] featureNames, final ConsentChangeSource changeSource) {
        L.d("[ModuleConsent] removeConsentInternal, featureNames: [" + Arrays.toString(featureNames) + "], changeSource: [" + changeSource + "]");
        setConsentInternal(featureNames, false, changeSource);
    }

    /**
     * Remove consent for all features
     */
    public void removeConsentAllInternal(final ConsentChangeSource changeSource) {
        L.d("[ModuleConsent] removeConsentAllInternal, changeSource: [" + changeSource + "]");
        removeConsentInternal(validFeatureNames, changeSource);
    }

    @Override
    void initFinished(@NonNull final CountlyConfig config) {
        L.d("[ModuleConsent] initFinished, requiresConsent: [" + requiresConsent + "]");
        if (requiresConsent) {
            //do appropriate action regarding the current consent state

            //remove persistent push flag if no push consent was set
            doPushConsentSpecialAction(getConsentTrue(Countly.CountlyFeatureNames.push));

            //send 'after init' consent state
            String formattedConsentState = formatConsentState(featureConsentValues);
            L.d("[ModuleConsent] initFinished, Sending consent changes after init: [" + formattedConsentState + "]");
            requestQueueProvider.sendConsentChanges(formattedConsentState);

            if (L.logEnabled()) {
                checkAllConsentInternal();
            }
        }
    }

    @Override
    void onConsentChanged(@NonNull final List<String> consentChangeDelta, final boolean newConsent, @NonNull final ModuleConsent.ConsentChangeSource changeSource) {
        L.d("[ModuleConsent] onConsentChanged, consentChangeDelta: [" + consentChangeDelta + "], newConsent: [" + newConsent + "], changeSource: [" + changeSource + "]");
        if (consentChangeDelta.contains(Countly.CountlyFeatureNames.push)) {
            //handle push consent changes
            doPushConsentSpecialAction(newConsent);
        }
    }

    @Override
    void halt() {
        consentInterface = null;
    }

    public class Consent {
        /**
         * Print the consent values of all features
         */
        public void checkAllConsent() {
            L.i("[Countly] checkAllConsent");
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
            L.i("[Countly] getConsent, featureName: [" + featureName + "]");
            synchronized (_cly) {
                return getConsentInternal(featureName);
            }
        }

        /**
         * Remove consent for all features
         */
        public void removeConsentAll() {
            L.i("[Countly] removeConsentAll");
            synchronized (_cly) {
                removeConsentAllInternal(ConsentChangeSource.ChangeConsentCall);
            }
        }

        /**
         * Remove the consent of a feature
         *
         * @param featureNames the names of features for which consent should be removed
         */
        public void removeConsent(@Nullable final String[] featureNames) {
            L.i("[Countly] removeConsent");
            synchronized (_cly) {
                removeConsentInternal(featureNames, ConsentChangeSource.ChangeConsentCall);
            }
        }

        /**
         * Gives consent for all features
         */
        public void giveConsentAll() {
            synchronized (_cly) {
                L.i("[Consent] Giving consent for all features");

                setConsentInternal(validFeatureNames, true, ConsentChangeSource.ChangeConsentCall);
            }
        }

        /**
         * Give the consent to a feature
         *
         * @param featureNames the names of features for which consent should be given
         */
        public void giveConsent(@Nullable final String[] featureNames) {
            L.i("[Countly] giveConsent");
            synchronized (_cly) {
                setConsentInternal(featureNames, true, ConsentChangeSource.ChangeConsentCall);
            }
        }

        /**
         * Set the consent of a feature
         *
         * @param featureNames feature names for which consent should be changed
         * @param isConsentGiven the consent value that should be set
         */
        public void setConsent(@Nullable final String[] featureNames, final boolean isConsentGiven) {
            L.i("[Countly] setConsent");
            synchronized (_cly) {
                setConsentInternal(featureNames, isConsentGiven, ConsentChangeSource.ChangeConsentCall);
            }
        }

        /**
         * Set the consent of a feature group
         *
         * @param groupName name of the consent group
         * @param isConsentGiven the value that should be set for this consent group
         */
        public void setConsentFeatureGroup(@Nullable final String groupName, final boolean isConsentGiven) {
            L.i("[Countly] setConsentFeatureGroup, groupName: [" + groupName + "], isConsentGiven: [" + isConsentGiven + "]");
            synchronized (_cly) {
                if (!groupedFeatures.containsKey(groupName)) {
                    L.d("[Countly] setConsentFeatureGroup, Trying to set consent for a unknown feature group: [" + groupName + "]");

                    return;
                }

                setConsentInternal(groupedFeatures.get(groupName), isConsentGiven, ConsentChangeSource.ChangeConsentCall);
            }
        }

        /**
         * Group multiple features into a feature group
         *
         * @param groupName name of the consent group
         * @param features array of feature to be added to the consent group
         */
        public void createFeatureGroup(@Nullable final String groupName, @Nullable final String[] features) {
            L.i("[Countly] createFeatureGroup, groupName: [" + groupName + "], features: [" + Arrays.toString(features) + "]");
            synchronized (_cly) {
                groupedFeatures.put(groupName, features);
            }
        }
    }
}
