package ly.count.android.sdk;

public class ConfigSdkInternalLimits {
    //SDK internal limits
    protected Integer maxKeyLength;
    protected Integer maxValueSize;
    protected Integer maxSegmentationValues;
    protected Integer maxBreadcrumbCount;
    protected Integer maxStackTraceLinesPerThread;
    protected Integer maxStackTraceLineLength;
    protected int maxStackTraceThreadCount = 50;

    /**
     * Sets how many segmentation values can be recorded when recording an event or view.
     * Values exceeding this count will be ignored.
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
     *
     * @param maxBreadcrumbCount to set
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigSdkInternalLimits setMaxBreadcrumbCount(int maxBreadcrumbCount) {
        this.maxBreadcrumbCount = maxBreadcrumbCount;
        return this;
    }

    public synchronized ConfigSdkInternalLimits setMaxKeyLength(int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
        return this;
    }

    /**
     * Set the maximum value size for values used internally. This affects things like: segmentation values
     * user property values, breadcrumb text.
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
     *
     * @param maxStackTraceLineLength to set
     * @return Returns the same config object for convenient linking
     */
    public synchronized ConfigSdkInternalLimits setMaxStackTraceLineLength(int maxStackTraceLineLength) {
        this.maxStackTraceLineLength = maxStackTraceLineLength;
        return this;
    }
}
