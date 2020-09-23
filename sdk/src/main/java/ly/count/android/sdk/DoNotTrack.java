package ly.count.android.sdk;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Classes annotated with DoNotTrack will not be automatically tracked as views
 * by Countly. Thus, this annotation can be used as a means to selectively opt-out
 * of automatic view tracking even when {@link CountlyConfig#enableViewTracking} is true
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DoNotTrack { }
