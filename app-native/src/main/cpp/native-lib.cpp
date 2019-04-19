#include <jni.h>
#include <string>

#include <android/log.h>

#ifndef DEBUG
#define DEBUG 1
#endif

#ifdef DEBUG
#define TAG_NAME "COUNTLY_DEMO_CRASH_cpp"
#define LOGV(...) __android_log_print(2, TAG_NAME, __VA_ARGS__)
#define LOGD(...) __android_log_print(3, TAG_NAME, __VA_ARGS__)
#define LOGI(...) __android_log_print(4, TAG_NAME, __VA_ARGS__)
#define LOGW(...) __android_log_print(5, TAG_NAME, __VA_ARGS__)
#define LOGE(...) __android_log_print(6, TAG_NAME, __VA_ARGS__)
#else
#define LOGV(...) {}
	#define LOGD(...) {}
	#define LOGI(...) {}
	#define LOGW(...) {}
	#define LOGE(...) {}
#endif

extern "C" {
JNIEXPORT jstring JNICALL
Java_ly_count_android_demo_crash_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

JNIEXPORT jint JNICALL Java_ly_count_android_demo_crash_MainActivity_testCrash(JNIEnv *env,
                                                                         jobject obj) {
    LOGE("native crash capture begin");
    char *ptr = NULL;
    *ptr = 1;
    LOGE("native crash capture end");
    return 0;
}

}