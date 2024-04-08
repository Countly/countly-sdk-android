package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.PrintWriter;
import java.io.Writer;

public class AutoTruncatePrintWriter extends PrintWriter {
    private final int maxValueSize;
    private final ModuleLog L;
    private final String tag;

    public AutoTruncatePrintWriter(@NonNull Writer out, final int maxSize, @NonNull ModuleLog L, @NonNull String tag) {
        super(out);
        assert maxSize > 0;
        assert out != null;
        assert L != null;
        assert tag != null;

        this.maxValueSize = maxSize;
        this.L = L;
        this.tag = tag;
    }

    @Override
    public void println(@Nullable String value) {
        if (value != null && value.length() > maxValueSize) {
            super.println(value.substring(0, maxValueSize));
            L.w(tag + ": [AutoTruncatePrintWriter] println, Value was truncated to " + maxValueSize + " characters");
        } else {
            super.println(value);
        }
    }
}
