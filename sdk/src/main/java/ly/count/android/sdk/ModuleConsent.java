package ly.count.android.sdk;

import android.util.Log;

public class ModuleConsent extends ModuleBase {

    Consent consentInterface = null;

    ModuleConsent(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleConsent] Initialising");
        }

        consentInterface = new Consent();
    }

    @Override
    public void halt() {
        consentInterface = null;
    }

    public class Consent {
        /**
         * Print the consent values of all features
         *
         * @return Returns link to Countly for call chaining
         */
        public synchronized void checkAllConsent() {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[Consent] calling checkAllConsent");
            }

            _cly.checkAllConsent();
        }

        /**
         * Get the current consent state of a feature
         *
         * @param featureName the name of a feature for which consent should be checked
         * @return the consent value
         */
        public synchronized boolean getConsent(String featureName) {
            return _cly.getConsent(featureName);
        }

        /**
         * Remove consent for all features
         *
         * @return Returns link to Countly for call chaining
         */
        public synchronized void removeConsentAll() {
            _cly.removeConsentAll();
        }

        /**
         * Remove the consent of a feature
         *
         * @param featureNames the names of features for which consent should be removed
         * @return Returns link to Countly for call chaining
         */
        public synchronized void removeConsent(String[] featureNames) {
            _cly.removeConsent(featureNames);
        }

        /**
         * Gives consent for all features
         *
         * @return Returns link to Countly for call chaining
         */
        public synchronized void giveConsentAll() {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "Giving consent for all features");
            }

            if (_cly.isLoggingEnabled() && !_cly.isInitialized()) {
                Log.w(Countly.TAG, "Calling this before initialising the SDK is deprecated!");
            }

            _cly.giveConsent(_cly.validFeatureNames);
        }

        /**
         * Give the consent to a feature
         *
         * @param featureNames the names of features for which consent should be given
         * @return Returns link to Countly for call chaining
         */
        public synchronized void giveConsent(String[] featureNames) {
            _cly.giveConsent(featureNames);
        }

        /**
         * Set the consent of a feature
         *
         * @param featureNames feature names for which consent should be changed
         * @param isConsentGiven the consent value that should be set
         * @return Returns link to Countly for call chaining
         */
        public synchronized void setConsent(String[] featureNames, boolean isConsentGiven) {
            _cly.setConsent(featureNames, isConsentGiven);
        }

        /**
         * Set the consent of a feature group
         *
         * @param groupName name of the consent group
         * @param isConsentGiven the value that should be set for this consent group
         * @return Returns link to Countly for call chaining
         */
        public synchronized void setConsentFeatureGroup(String groupName, boolean isConsentGiven) {
            _cly.setConsentFeatureGroup(groupName, isConsentGiven);
        }

        /**
         * Group multiple features into a feature group
         *
         * @param groupName name of the consent group
         * @param features array of feature to be added to the consent group
         * @return Returns link to Countly for call chaining
         */
        public synchronized void createFeatureGroup(String groupName, String[] features) {
            _cly.createFeatureGroup(groupName, features);
        }
    }
}
