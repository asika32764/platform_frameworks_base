LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := services.print

LOCAL_SRC_FILES += \
      $(call all-java-files-under,java)

include $(BUILD_STATIC_JAVA_LIBRARY)
