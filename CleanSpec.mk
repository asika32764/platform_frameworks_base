# Copyright (C) 2007 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# If you don't need to do a full clean build but would like to touch
# a file or delete some intermediate files, add a clean step to the end
# of the list.  These steps will only be run once, if they haven't been
# run before.
#
# E.g.:
#     $(call add-clean-step, touch -c external/sqlite/sqlite3.h)
#     $(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/STATIC_LIBRARIES/libz_intermediates)
#
# Always use "touch -c" and "rm -f" or "rm -rf" to gracefully deal with
# files that are missing or have been moved.
#
# Use $(PRODUCT_OUT) to get to the "out/target/product/blah/" directory.
# Use $(OUT_DIR) to refer to the "out" directory.
#
# If you need to re-do something that's already mentioned, just copy
# the command and add it to the bottom of the list.  E.g., if a change
# that you made last week required touching a file and a change you
# made today requires touching the same file, just copy the old
# touch step and add it to the end of the list.
#
# *****************************************************************
# NEWER CLEAN STEPS MUST BE AT THE END OF THE LIST ABOVE THE BANNER
# *****************************************************************

# For example:
#$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/AndroidTests_intermediates)
#$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/core_intermediates)
#$(call add-clean-step, find $(OUT_DIR) -type f -name "IGTalkSession*" -print0 | xargs -0 rm -f)
#$(call add-clean-step, rm -rf $(PRODUCT_OUT)/data/*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/com/android/internal/os/IDropBoxService.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/backup)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/com/android/internal/backup)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/backup)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/com/android/internal/backup)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/app)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/content)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/FrameworkTest_intermediates/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android.policy*)
$(call add-clean-step, rm -rf $(TARGET_OUT_JAVA_LIBRARIES)/android.policy.jar)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/obj/lib/libequalizer.so)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/obj/lib/libequalizertest.so)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/obj/lib/libreverb.so)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/obj/lib/libreverbtest.so)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/symbols/system/lib/libequalizer.so)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/symbols/system/lib/libequalizertest.so)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/symbols/system/lib/libreverb.so)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/symbols/system/lib/libreverbtest.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libequalizer_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libequalizertest_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libreverb_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libreverbtest_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib/soundfx/)
$(call add-clean-step, find . -type f -name "*.rs" -print0 | xargs -0 touch)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libandroid_runtime_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/lib/libandroid_runtime.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/symbols/system/lib/libandroid_runtime.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib/libandroid_runtime.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libhwui_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/lib/libhwui.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/symbols/system/lib/libhwui.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib/libhwui.so)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/os/storage/*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/content/IClipboard.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/telephony/java/com/android/internal/telephony/ITelephonyRegistry.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android_stubs_current_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/docs/api-stubs*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/com/trustedlogic)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android_stubs_current_intermediates/src/com/trustedlogic)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/com/trustedlogic)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android_stubs_current_intermediates/src/com/trustedlogic)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/Music2_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/nfc/INdefTag.java)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/STATIC_LIBRARIES/libstagefright_aacdec_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/STATIC_LIBRARIES/libstagefright_mp3dec_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/build.prop)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/ImageProcessing_intermediates/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/ModelViewer_intermediates/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/PerfTest_intermediates/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/RSTest_intermediates/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/hardware/IUsbManager.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/nfc)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libstagefright_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/os)
$(call add-clean-step, rm -rf $(OUT_DIR)target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/keystore/java/android/security/IKeyChainAliasResponse.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/vpn)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/nfc)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/SystemUI_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/R/com/android/systemui/R.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/media/java/android/media/IAudioService.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/media/java/android/media/IAudioService.P)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/media/audio/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/nfc/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/wifi/java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/wifi/java)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/media/audio/)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/fonts/Lohit_Hindi.ttf)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/media/audio/)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/fonts/DroidSans*)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/media/audio/)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/fonts/DroidSans*)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/media/audio/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/content)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android_stubs_current_intermediates/src/android/content)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/*/src/renderscript)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/*/src/renderscript)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/*/src/RenderScript.stamp)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/*/src/RenderScript.stamp)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libRS_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/STATIC_LIBRARIES/libRSDriver_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/host/$(HOST_PREBUILT_TAG)/obj/STATIC_LIBRARIES/libRS_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/accessibilityservice/IEventListener.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/accessibilityservice/IEventListener.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/accessibility/IAccessibilityManager.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/accessibilityservice/IAccessibilityServiceClientCallback.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/accessibilityservice/IAccessibilityServiceClientCallback.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/accessibilityservice/IAccessibilityServiceClient.P)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/system/media/video/Disco*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/ImageProcessing_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/ImageProcessing2_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/ImageProcessing_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/media/audio/ui/*.ogg)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/telephony/java/com/android/internal/telephony/IExtendedNetworkService.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/telephony/java/com/android/internal/telephony/IExtendedNetworkService.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/ImageProcessing_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/IDisplayContentChangeListener.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/IDisplayContentChangeListener.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/IWindowManager.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/IWindowManager.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/IDisplayMagnificationController.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/IDisplayMagnificationController.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/IDisplayMagnificationMediator.java)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/android/view/IDisplayMagnificationMediator.P)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/voip)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/librtp_jni_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib/librtp_jni.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/lib/librtp_jni.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/symbols/system/lib/librtp_jni.so)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/telephony/java/com/android/internal/telephony/SmsRawData.*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/com/android/internal/view/IInputMethodCallback.*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/com/android/internal/view/IInputMethodSession.*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java/com/android/internal/view/IInputMethodCallback.*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/framework-res_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -rf $(HOST_OUT)/obj/STATIC_LIBRARIES/libandroidfw_intermediates/import_includes)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/print/IPrinterDiscoveryObserver.*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/print/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/printservice/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/packages/services/Proxy/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/print/IPrinterDiscoverySessionObserver.*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/print/IPrinterDiscoverySessionClient.*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/os/IBattery*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android_stubs_current_intermediates)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/usr/idc/frameworks)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/usr/keylayout/frameworks)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/usr/keychars/frameworks)
$(call add-clean-step, rm -f $(PRODUCT_OUT)/system/media/video/*)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/media/audio/)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/media/audio/effects/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/framework-res_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/print/IPrintClient.*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/services_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/media/java/android/media/IMedia*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/view/IMagnificationCallbacks*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/tv/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/media/java/android/media/)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/app)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android_stubs_current_intermediates/src/android/app/wearable)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/tv/ITv*)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/classes/android/app/task)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/app/task)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/classes/android/app/TaskManager)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/src/core/java/android/app/maintenance)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework-base_intermediates/classes/android/app/maintenance)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes/android/app/maintenance)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android_stubs_current_intermediates/classes/android/app/maintenance)
$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/android_stubs_current_intermediates/src/android/app/maintenance)

# ******************************************************************
# NEWER CLEAN STEPS MUST BE AT THE END OF THE LIST ABOVE THIS BANNER
# ******************************************************************
