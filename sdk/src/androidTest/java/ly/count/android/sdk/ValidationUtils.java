package ly.count.android.sdk;

public class ValidationUtils {

    private ValidationUtils() {
    }

    protected static void validateConsentRequest(String deviceId, int idx, boolean[] consents) {
        String consentParam = "{\"sessions\":%b,\"crashes\":%b,\"users\":%b,\"push\":%b,\"feedback\":%b,\"scrolls\":%b,\"remote-config\":%b,\"attribution\":%b,\"clicks\":%b,\"location\":%b,\"star-rating\":%b,\"events\":%b,\"views\":%b,\"apm\":%b}";
        String consentsStr = String.format(consentParam, consents[0], consents[1], consents[2], consents[3], consents[4], consents[5], consents[6], consents[7], consents[8], consents[9], consents[10], consents[11], consents[12], consents[13]);
        TestUtils.validateRequest(deviceId, TestUtils.map("consent", consentsStr), idx);
    }

    protected static void validateNoConsentRequest(String deviceId, int idx) {
        validateConsentRequest(deviceId, idx, new boolean[] { false, false, false, false, false, false, false, false, false, false, false, false, false, false });
    }

    protected static void validateAllConsentRequest(String deviceId, int idx) {
        validateConsentRequest(deviceId, idx, new boolean[] { true, true, true, true, true, true, true, true, true, true, true, true, true, true });
    }
}
