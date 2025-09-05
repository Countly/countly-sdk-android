package ly.count.android.sdk;

interface ConfigurationValueValidator<T> {

    Boolean validate(T value);
}
