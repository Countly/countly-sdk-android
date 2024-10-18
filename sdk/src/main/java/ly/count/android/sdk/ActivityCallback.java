package ly.count.android.sdk;

import org.json.JSONObject;

interface ActivityCallback {
    /**
     * Close the activity when in need
     */
    void closeActivity();

    /**
     * Resize the activity when in need
     *
     * @param coordinates the new coordinates for the activity in a form of a JSON object
     * with the following keys:
     * - p: x, y, w, h
     * - l: x, y, w, h
     * p - portrait
     * l - landscape
     * x - x coordinate
     * y - y coordinate
     */
    void resizeActivity(JSONObject coordinates);
}
