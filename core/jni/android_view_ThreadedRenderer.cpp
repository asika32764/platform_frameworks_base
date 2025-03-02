/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "ThreadedRenderer"

#include <algorithm>

#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <EGL/egl_cache.h>

#include <utils/StrongPointer.h>
#include <android_runtime/android_view_Surface.h>
#include <system/window.h>

#include "android_view_GraphicBuffer.h"

#include <Animator.h>
#include <RenderNode.h>
#include <renderthread/CanvasContext.h>
#include <renderthread/RenderProxy.h>
#include <renderthread/RenderTask.h>
#include <renderthread/RenderThread.h>
#include <Vector.h>

namespace android {

#ifdef USE_OPENGL_RENDERER

using namespace android::uirenderer;
using namespace android::uirenderer::renderthread;

static jmethodID gRunnableMethod;

static JNIEnv* getenv(JavaVM* vm) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOG_ALWAYS_FATAL("Failed to get JNIEnv for JavaVM: %p", vm);
    }
    return env;
}

class JavaTask : public RenderTask {
public:
    JavaTask(JNIEnv* env, jobject jrunnable) {
        env->GetJavaVM(&mVm);
        mRunnable = env->NewGlobalRef(jrunnable);
    }

    virtual void run() {
        JNIEnv* env = getenv(mVm);
        env->CallVoidMethod(mRunnable, gRunnableMethod);
        env->DeleteGlobalRef(mRunnable);
        delete this;
    };

private:
    JavaVM* mVm;
    jobject mRunnable;
};

class SetAtlasTask : public RenderTask {
public:
    SetAtlasTask(const sp<GraphicBuffer>& buffer, int64_t* map, size_t size)
            : mBuffer(buffer)
            , mMap(map)
            , mMapSize(size) {
    }

    virtual void run() {
        CanvasContext::setTextureAtlas(mBuffer, mMap, mMapSize);
        mMap = 0;
        delete this;
    }

private:
    sp<GraphicBuffer> mBuffer;
    int64_t* mMap;
    size_t mMapSize;
};

class OnFinishedEvent {
public:
    OnFinishedEvent(BaseRenderNodeAnimator* animator, AnimationListener* listener)
            : animator(animator), listener(listener) {}
    sp<BaseRenderNodeAnimator> animator;
    sp<AnimationListener> listener;
};

class InvokeAnimationListeners : public MessageHandler {
public:
    InvokeAnimationListeners(std::vector<OnFinishedEvent>& events) {
        mOnFinishedEvents.swap(events);
    }

    static void callOnFinished(OnFinishedEvent& event) {
        event.listener->onAnimationFinished(event.animator.get());
    }

    virtual void handleMessage(const Message& message) {
        std::for_each(mOnFinishedEvents.begin(), mOnFinishedEvents.end(), callOnFinished);
        mOnFinishedEvents.clear();
    }

private:
    std::vector<OnFinishedEvent> mOnFinishedEvents;
};

class RenderingException : public MessageHandler {
public:
    RenderingException(JavaVM* vm, const std::string& message)
            : mVm(vm)
            , mMessage(message) {
    }

    virtual void handleMessage(const Message&) {
        throwException(mVm, mMessage);
    }

    static void throwException(JavaVM* vm, const std::string& message) {
        JNIEnv* env = getenv(vm);
        jniThrowException(env, "java/lang/IllegalStateException", message.c_str());
    }

private:
    JavaVM* mVm;
    std::string mMessage;
};

class RootRenderNode : public RenderNode, AnimationHook, ErrorHandler {
public:
    RootRenderNode(JNIEnv* env) : RenderNode() {
        mLooper = Looper::getForThread();
        LOG_ALWAYS_FATAL_IF(!mLooper.get(),
                "Must create RootRenderNode on a thread with a looper!");
        env->GetJavaVM(&mVm);
    }

    virtual ~RootRenderNode() {}

    virtual void callOnFinished(BaseRenderNodeAnimator* animator, AnimationListener* listener) {
        OnFinishedEvent event(animator, listener);
        mOnFinishedEvents.push_back(event);
    }

    virtual void onError(const std::string& message) {
        mLooper->sendMessage(new RenderingException(mVm, message), 0);
    }

    virtual void prepareTree(TreeInfo& info) {
        info.animationHook = this;
        info.errorHandler = this;
        RenderNode::prepareTree(info);
        info.animationHook = NULL;
        info.errorHandler = NULL;

        // post all the finished stuff
        if (mOnFinishedEvents.size()) {
            sp<InvokeAnimationListeners> message
                    = new InvokeAnimationListeners(mOnFinishedEvents);
            mLooper->sendMessage(message, 0);
        }
    }

protected:
    virtual void damageSelf(TreeInfo& info) {
        // Intentionally a no-op. As RootRenderNode gets a new DisplayListData
        // every frame this would result in every draw push being a full inval,
        // which is wrong. Only RootRenderNode has this issue.
    }

private:
    sp<Looper> mLooper;
    std::vector<OnFinishedEvent> mOnFinishedEvents;
    JavaVM* mVm;
};

static void android_view_ThreadedRenderer_setAtlas(JNIEnv* env, jobject clazz,
        jobject graphicBuffer, jlongArray atlasMapArray) {
    sp<GraphicBuffer> buffer = graphicBufferForJavaObject(env, graphicBuffer);
    jsize len = env->GetArrayLength(atlasMapArray);
    if (len <= 0) {
        ALOGW("Failed to initialize atlas, invalid map length: %d", len);
        return;
    }
    int64_t* map = new int64_t[len];
    env->GetLongArrayRegion(atlasMapArray, 0, len, map);

    SetAtlasTask* task = new SetAtlasTask(buffer, map, len);
    RenderThread::getInstance().queue(task);
}

static jlong android_view_ThreadedRenderer_createRootRenderNode(JNIEnv* env, jobject clazz) {
    RootRenderNode* node = new RootRenderNode(env);
    node->incStrong(0);
    node->setName("RootRenderNode");
    return reinterpret_cast<jlong>(node);
}

static jlong android_view_ThreadedRenderer_createProxy(JNIEnv* env, jobject clazz,
        jboolean translucent, jlong rootRenderNodePtr) {
    RenderNode* rootRenderNode = reinterpret_cast<RenderNode*>(rootRenderNodePtr);
    return (jlong) new RenderProxy(translucent, rootRenderNode);
}

static void android_view_ThreadedRenderer_deleteProxy(JNIEnv* env, jobject clazz,
        jlong proxyPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    delete proxy;
}

static void android_view_ThreadedRenderer_setFrameInterval(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jlong frameIntervalNanos) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    proxy->setFrameInterval(frameIntervalNanos);
}

static jboolean android_view_ThreadedRenderer_loadSystemProperties(JNIEnv* env, jobject clazz,
        jlong proxyPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    return proxy->loadSystemProperties();
}

static jboolean android_view_ThreadedRenderer_initialize(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jobject jsurface) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    sp<ANativeWindow> window = android_view_Surface_getNativeWindow(env, jsurface);
    return proxy->initialize(window);
}

static void android_view_ThreadedRenderer_updateSurface(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jobject jsurface) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    sp<ANativeWindow> window;
    if (jsurface) {
        window = android_view_Surface_getNativeWindow(env, jsurface);
    }
    proxy->updateSurface(window);
}

static void android_view_ThreadedRenderer_pauseSurface(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jobject jsurface) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    sp<ANativeWindow> window;
    if (jsurface) {
        window = android_view_Surface_getNativeWindow(env, jsurface);
    }
    proxy->pauseSurface(window);
}

static void android_view_ThreadedRenderer_setup(JNIEnv* env, jobject clazz, jlong proxyPtr,
        jint width, jint height,
        jfloat lightX, jfloat lightY, jfloat lightZ, jfloat lightRadius) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    proxy->setup(width, height, Vector3(lightX, lightY, lightZ), lightRadius);
}

static void android_view_ThreadedRenderer_setOpaque(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jboolean opaque) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    proxy->setOpaque(opaque);
}

static int android_view_ThreadedRenderer_syncAndDrawFrame(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jlong frameTimeNanos, jlong recordDuration, jfloat density) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    return proxy->syncAndDrawFrame(frameTimeNanos, recordDuration, density);
}

static void android_view_ThreadedRenderer_destroyCanvasAndSurface(JNIEnv* env, jobject clazz,
        jlong proxyPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    proxy->destroyCanvasAndSurface();
}

static void android_view_ThreadedRenderer_invokeFunctor(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jlong functorPtr, jboolean waitForCompletion) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    Functor* functor = reinterpret_cast<Functor*>(functorPtr);
    proxy->invokeFunctor(functor, waitForCompletion);
}

static void android_view_ThreadedRenderer_runWithGlContext(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jobject jrunnable) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    RenderTask* task = new JavaTask(env, jrunnable);
    proxy->runWithGlContext(task);
}

static jlong android_view_ThreadedRenderer_createDisplayListLayer(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jint width, jint height) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    DeferredLayerUpdater* layer = proxy->createDisplayListLayer(width, height);
    return reinterpret_cast<jlong>(layer);
}

static jlong android_view_ThreadedRenderer_createTextureLayer(JNIEnv* env, jobject clazz,
        jlong proxyPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    DeferredLayerUpdater* layer = proxy->createTextureLayer();
    return reinterpret_cast<jlong>(layer);
}

static jboolean android_view_ThreadedRenderer_copyLayerInto(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jlong layerPtr, jlong bitmapPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerPtr);
    SkBitmap* bitmap = reinterpret_cast<SkBitmap*>(bitmapPtr);
    return proxy->copyLayerInto(layer, bitmap);
}

static void android_view_ThreadedRenderer_pushLayerUpdate(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jlong layerPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerPtr);
    proxy->pushLayerUpdate(layer);
}

static void android_view_ThreadedRenderer_cancelLayerUpdate(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jlong layerPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerPtr);
    proxy->cancelLayerUpdate(layer);
}

static void android_view_ThreadedRenderer_flushCaches(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jint flushMode) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    proxy->flushCaches(static_cast<Caches::FlushMode>(flushMode));
}

static void android_view_ThreadedRenderer_fence(JNIEnv* env, jobject clazz,
        jlong proxyPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    proxy->fence();
}

static void android_view_ThreadedRenderer_notifyFramePending(JNIEnv* env, jobject clazz,
        jlong proxyPtr) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    proxy->notifyFramePending();
}

static void android_view_ThreadedRenderer_dumpProfileInfo(JNIEnv* env, jobject clazz,
        jlong proxyPtr, jobject javaFileDescriptor) {
    RenderProxy* proxy = reinterpret_cast<RenderProxy*>(proxyPtr);
    int fd = jniGetFDFromFileDescriptor(env, javaFileDescriptor);
    proxy->dumpProfileInfo(fd);
}

#endif

// ----------------------------------------------------------------------------
// Shaders
// ----------------------------------------------------------------------------

static void android_view_ThreadedRenderer_setupShadersDiskCache(JNIEnv* env, jobject clazz,
        jstring diskCachePath) {

    const char* cacheArray = env->GetStringUTFChars(diskCachePath, NULL);
    egl_cache_t::get()->setCacheFilename(cacheArray);
    env->ReleaseStringUTFChars(diskCachePath, cacheArray);
}

// ----------------------------------------------------------------------------
// JNI Glue
// ----------------------------------------------------------------------------

const char* const kClassPathName = "android/view/ThreadedRenderer";

static JNINativeMethod gMethods[] = {
#ifdef USE_OPENGL_RENDERER
    { "nSetAtlas", "(Landroid/view/GraphicBuffer;[J)V",   (void*) android_view_ThreadedRenderer_setAtlas },
    { "nCreateRootRenderNode", "()J", (void*) android_view_ThreadedRenderer_createRootRenderNode },
    { "nCreateProxy", "(ZJ)J", (void*) android_view_ThreadedRenderer_createProxy },
    { "nDeleteProxy", "(J)V", (void*) android_view_ThreadedRenderer_deleteProxy },
    { "nSetFrameInterval", "(JJ)V", (void*) android_view_ThreadedRenderer_setFrameInterval },
    { "nLoadSystemProperties", "(J)Z", (void*) android_view_ThreadedRenderer_loadSystemProperties },
    { "nInitialize", "(JLandroid/view/Surface;)Z", (void*) android_view_ThreadedRenderer_initialize },
    { "nUpdateSurface", "(JLandroid/view/Surface;)V", (void*) android_view_ThreadedRenderer_updateSurface },
    { "nPauseSurface", "(JLandroid/view/Surface;)V", (void*) android_view_ThreadedRenderer_pauseSurface },
    { "nSetup", "(JIIFFFF)V", (void*) android_view_ThreadedRenderer_setup },
    { "nSetOpaque", "(JZ)V", (void*) android_view_ThreadedRenderer_setOpaque },
    { "nSyncAndDrawFrame", "(JJJF)I", (void*) android_view_ThreadedRenderer_syncAndDrawFrame },
    { "nDestroyCanvasAndSurface", "(J)V", (void*) android_view_ThreadedRenderer_destroyCanvasAndSurface },
    { "nInvokeFunctor", "(JJZ)V", (void*) android_view_ThreadedRenderer_invokeFunctor },
    { "nRunWithGlContext", "(JLjava/lang/Runnable;)V", (void*) android_view_ThreadedRenderer_runWithGlContext },
    { "nCreateDisplayListLayer", "(JII)J", (void*) android_view_ThreadedRenderer_createDisplayListLayer },
    { "nCreateTextureLayer", "(J)J", (void*) android_view_ThreadedRenderer_createTextureLayer },
    { "nCopyLayerInto", "(JJJ)Z", (void*) android_view_ThreadedRenderer_copyLayerInto },
    { "nPushLayerUpdate", "(JJ)V", (void*) android_view_ThreadedRenderer_pushLayerUpdate },
    { "nCancelLayerUpdate", "(JJ)V", (void*) android_view_ThreadedRenderer_cancelLayerUpdate },
    { "nFlushCaches", "(JI)V", (void*) android_view_ThreadedRenderer_flushCaches },
    { "nFence", "(J)V", (void*) android_view_ThreadedRenderer_fence },
    { "nNotifyFramePending", "(J)V", (void*) android_view_ThreadedRenderer_notifyFramePending },
    { "nDumpProfileInfo", "(JLjava/io/FileDescriptor;)V", (void*) android_view_ThreadedRenderer_dumpProfileInfo },
#endif
    { "setupShadersDiskCache", "(Ljava/lang/String;)V",
                (void*) android_view_ThreadedRenderer_setupShadersDiskCache },
};

int register_android_view_ThreadedRenderer(JNIEnv* env) {
#ifdef USE_OPENGL_RENDERER
    jclass cls = env->FindClass("java/lang/Runnable");
    gRunnableMethod = env->GetMethodID(cls, "run", "()V");
    env->DeleteLocalRef(cls);
#endif
    return AndroidRuntime::registerNativeMethods(env, kClassPathName, gMethods, NELEM(gMethods));
}

}; // namespace android
