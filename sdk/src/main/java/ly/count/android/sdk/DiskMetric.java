package ly.count.android.sdk;

/**
 * Holds disk usage metrics in megabytes.
 */
public class DiskMetric {
    /**
     * Total disk space in megabytes
     */
    public final String totalMb;

    /**
     * Used (current) disk space in megabytes
     */
    public final String usedMb;

    public DiskMetric(String totalMb, String usedMb) {
        this.totalMb = totalMb;
        this.usedMb = usedMb;
    }
}
