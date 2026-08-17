package ly.count.android.sdk;

import androidx.annotation.NonNull;

public class ConfigSdkInternalLimits {
    //SDK internal limits
    protected Integer maxKeyLength;
    protected Integer maxValueSize;
    protected int maxValueSizePicture = 4096;
    protected Integer maxSegmentationValues;
    protected Integer maxBreadcrumbCount;
    protected Integer maxStackTraceLinesPerThread;
    protected Integer maxStackTraceLineLength;
    protected int maxStackTraceThreadCount = 50;

    /**
     * Copies every limit onto this object. Each Countly instance keeps its own limits (see
     * {@code Countly#sdkInternalLimits_}) seeded from the developer's config, because the server behaviour
     * settings resolve these values per instance and two instances may be configured from one CountlyConfig.
     * <p>
     * ADDING A FIELD ABOVE MEANS ADDING IT HERE. {@code ConfigSdkInternalLimitsTests} reflects over the
     * declared fields and fails if one is missed, so a forgotten field cannot ship silently.
     */
    /**
     * Raises any set limit below 1 to 1. Called after the server behaviour settings have been applied, so a
     * server sending a nonsensical limit can not make the SDK truncate everything to nothing.
     */
    void clampToMinimums() {
        if (maxKeyLength != null) {
            maxKeyLength = Math.max(maxKeyLength, 1);
        }
        if (maxValueSize != null) {
            maxValueSize = Math.max(maxValueSize, 1);
        }
        if (maxSegmentationValues != null) {
            maxSegmentationValues = Math.max(maxSegmentationValues, 1);
        }
        if (maxBreadcrumbCount != null) {
            maxBreadcrumbCount = Math.max(maxBreadcrumbCount, 1);
        }
        if (maxStackTraceLinesPerThread != null) {
            maxStackTraceLinesPerThread = Math.max(maxStackTraceLinesPerThread, 1);
        }
        if (maxStackTraceLineLength != null) {
            maxStackTraceLineLength = Math.max(maxStackTraceLineLength, 1);
        }
    }

    void copyFrom(@NonNull ConfigSdkInternalLimits other) {
        maxKeyLength = other.maxKeyLength;
        maxValueSize = other.maxValueSize;
        maxValueSizePicture = other.maxValueSizePicture;
        maxSegmentationValues = other.maxSegmentationValues;
        maxBreadcrumbCount = other.maxBreadcrumbCount;
        maxStackTraceLinesPerThread = other.maxStackTraceLinesPerThread;
        maxStackTraceLineLength = other.maxStackTraceLineLength;
        maxStackTraceThreadCount = other.maxStackTraceThreadCount;
    }

    /**
     * Sets how many segmentation values can be recorded when recording an event or view.
     * Values exceeding this count will be ignored. The default value is 100 developer entries.
     *
     * @param maxSegmentationValues to set
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigSdkInternalLimits setMaxSegmentationValues(int maxSegmentationValues) {
        this.maxSegmentationValues = maxSegmentationValues;
        return this;
    }

    /**
     * Set the maximum amount of breadcrumbs that can be recorded.
     * After exceeding the limit, the oldest values will be removed.
     * Default value is 100.
     *
     * @param maxBreadcrumbCount to set
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigSdkInternalLimits setMaxBreadcrumbCount(int maxBreadcrumbCount) {
        this.maxBreadcrumbCount = maxBreadcrumbCount;
        return this;
    }

    /**
     * Set the maximum key length for keys used internally. This affects things like: event names, view names,
     * custom trace key name (APM), custom metric key (APM), segmentation keys, custom user property keys, custom user property keys
     * that are used for property modifiers (mul, push, pull, set, increment, etc)
     * Default value is 128.
     *
     * @param maxKeyLength to set
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigSdkInternalLimits setMaxKeyLength(int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
        return this;
    }

    /**
     * Set the maximum value size for values used internally.
     * - segmentation value in case of strings (for all features)
     * - custom user property string value
     * - user profile named key (username, email, etc) string values. Except "picture" field, that has a limit of 4096 chars
     * - custom user property modifier string values. For example, for modifiers like "push", "pull", "setOnce", etc.
     * - breadcrumb text
     * - manual feedback widget reporting fields (reported as event)
     * - rating widget response (reported as event)
     * Default value is 256.
     * If those values exceed the set limit, they will be truncated.
     *
     * @param maxValueSize to set
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigSdkInternalLimits setMaxValueSize(int maxValueSize) {
        this.maxValueSize = maxValueSize;
        return this;
    }

    /**
     * Set the maximum amount of stack trace lines that can be recorded per thread.
     * Default value is 30.
     *
     * @param maxStackTraceLinesPerThread to set
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigSdkInternalLimits setMaxStackTraceLinesPerThread(int maxStackTraceLinesPerThread) {
        this.maxStackTraceLinesPerThread = maxStackTraceLinesPerThread;
        return this;
    }

    /**
     * Set the maximum amount of stack trace threads that can be recorded.
     * Default value is 200.
     *
     * @param maxStackTraceLineLength to set
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigSdkInternalLimits setMaxStackTraceLineLength(int maxStackTraceLineLength) {
        this.maxStackTraceLineLength = maxStackTraceLineLength;
        return this;
    }
}
