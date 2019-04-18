ROOT_PATH := $(call my-dir)
include $(ROOT_PATH)/breakpad/android/google_breakpad/Android.mk

LOCAL_PATH := $(ROOT_PATH)
include $(CLEAR_VARS)

LOCAL_MODULE    := countly_native
LOCAL_SRC_FILES := countly_breakpad.cpp
LOCAL_LDLIBS := -llog
LOCAL_STATIC_LIBRARIES += breakpad_client

include $(BUILD_SHARED_LIBRARY)
