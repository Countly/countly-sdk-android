//
// Created by pembeci on 10.04.2019.
//

#include <string.h>
#include <jni.h>
#include "breakpad/src/config.h"
#include "breakpad/src/client/linux/handler/exception_handler.h"
#include "breakpad/src/client/linux/handler/minidump_descriptor.h"
#include <android/log.h>

#ifndef DEBUG
#define DEBUG 1
#endif

#ifdef DEBUG
#define TAG_NAME "countly_breakpad_cpp"
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

// we need this to get Java env in DumpCallback.
// See: https://stackoverflow.com/questions/12420463/keeping-a-global-reference-to-the-jnienv-environment

static JavaVM *jvm;
static jclass globalClass;
static jmethodID mid;
bool callbackJava = false;

bool DumpCallback(const google_breakpad::MinidumpDescriptor& descriptor,
                  void* context,
                  bool succeeded){
    LOGD("DumpCallback started");
    /*
    if (callbackJava) {
	    JNIEnv *env;
	    jint rs = jvm->AttachCurrentThread(&env, NULL);
	    assert (rs == JNI_OK);
	    LOGD("DumpCallback: thread attached");
	    env->CallStaticVoidMethod(globalClass, mid);
	    jboolean flag = env->ExceptionCheck();
	    if (flag) {
	      env->ExceptionClear();
	      LOGD("DumpCallback: exception occured in java callback.");
	    }
	    else {
	      LOGD("DumpCallback: java callback called successfully.");
	      jvm->DetachCurrentThread();
	    }
    }
    */
    LOGD("DumpCallback ==> succeeded %d path=%s", succeeded, descriptor.path());
    return succeeded;
}

JavaVM* gJvm = nullptr;

extern "C" {

// The following is needed to call Java from C++ but currently we don't use it.

/*
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *pjvm, void *reserved) {
    gJvm = pjvm;  // cache the JavaVM pointer
    LOGD("in JNI_OnLoad");
    JNIEnv *env;
    pjvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    LOGD("env ready");
    //replace with one of your classes in the line below
    auto myClass = env->FindClass("ly/count/android/CountlyNative");
    LOGD("class found");
    auto method1 = env->GetStaticMethodID(myClass, "crash", "()V");
    LOGD("crash found");
    mid = env->GetStaticMethodID(myClass, "processDump", "()V");
    LOGD("processDump found");
    env->CallStaticVoidMethod(myClass, mid);
    LOGD("processDump called");
    globalClass = reinterpret_cast<jclass>(env->NewGlobalRef(myClass));
    // mid = method2;
    return JNI_VERSION_1_6;
}
*/

JNIEXPORT jint JNICALL Java_ly_count_android_sdknative_CountlyNative_testCrash(JNIEnv* env,
                                                                           jobject obj){
    LOGE("native crash capture begin. this may take a few seconds.");
    char *ptr = NULL; *ptr = 1;
    LOGE("native crash capture end. not expected to reach this line.");
    return 0;
}

JNIEXPORT jint JNICALL Java_ly_count_android_sdknative_CountlyNative_init(JNIEnv* env,
                                                                      jobject jobj,
                                                                      jstring crash_dump_path){
    // store JavaVM in global variable jvm
    // jint rs = env->GetJavaVM(&jvm);
    // assert (rs == JNI_OK);

    // init breakpad
    const char* path = (char *)env->GetStringUTFChars(crash_dump_path, NULL);
    google_breakpad::MinidumpDescriptor descriptor(path);
    static google_breakpad::ExceptionHandler eh(descriptor, NULL, DumpCallback, NULL, true, -1);
    LOGD("breakpad initialized succeeded. dump files will be saved at %s", path);
    env->ReleaseStringUTFChars(crash_dump_path, path);
    return 1;
}

JNIEXPORT jstring JNICALL Java_ly_count_android_sdknative_CountlyNative_getBreakpadVersion(JNIEnv* env,
                                                                      jobject jobj){
    return env->NewStringUTF(VERSION);
}

JNIEXPORT jstring JNICALL Java_ly_count_android_sdknative_CountlyNative_getBreakpadChecksum(JNIEnv* env,
                                                                      jobject jobj){
    return env->NewStringUTF(CLY_HEAD_CHECKSUM);
}

}
