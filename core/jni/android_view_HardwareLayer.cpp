/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_TAG "OpenGLRenderer"

#include "jni.h"
#include "GraphicsJNI.h"
#include <nativehelper/JNIHelp.h>

#include <android_runtime/AndroidRuntime.h>
#include <android_runtime/android_graphics_SurfaceTexture.h>

#include <gui/GLConsumer.h>

#include <SkBitmap.h>
#include <SkCanvas.h>
#include <SkMatrix.h>
#include <SkPaint.h>
#include <SkXfermode.h>

#include <DeferredLayerUpdater.h>
#include <LayerRenderer.h>
#include <SkiaShader.h>
#include <Rect.h>
#include <RenderNode.h>

namespace android {

using namespace uirenderer;

#ifdef USE_OPENGL_RENDERER

static void android_view_HardwareLayer_onTextureDestroyed(JNIEnv* env, jobject clazz,
        jlong layerUpdaterPtr) {
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerUpdaterPtr);
    layer->backingLayer()->clearTexture();
}

static jboolean android_view_HardwareLayer_prepare(JNIEnv* env, jobject clazz,
        jlong layerUpdaterPtr, jint width, jint height, jboolean isOpaque) {
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerUpdaterPtr);
    bool changed = false;
    changed |= layer->setSize(width, height);
    changed |= layer->setBlend(!isOpaque);
    return changed;
}

static void android_view_HardwareLayer_setLayerPaint(JNIEnv* env, jobject clazz,
        jlong layerUpdaterPtr, jlong paintPtr) {
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerUpdaterPtr);
    if (layer) {
        SkPaint* paint = reinterpret_cast<SkPaint*>(paintPtr);
        layer->setPaint(paint);
    }
}

static void android_view_HardwareLayer_setTransform(JNIEnv* env, jobject clazz,
        jlong layerUpdaterPtr, jlong matrixPtr) {
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerUpdaterPtr);
    SkMatrix* matrix = reinterpret_cast<SkMatrix*>(matrixPtr);
    layer->setTransform(matrix);
}

static void android_view_HardwareLayer_setSurfaceTexture(JNIEnv* env, jobject clazz,
        jlong layerUpdaterPtr, jobject surface, jboolean isAlreadyAttached) {
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerUpdaterPtr);
    sp<GLConsumer> surfaceTexture(SurfaceTexture_getSurfaceTexture(env, surface));
    layer->setSurfaceTexture(surfaceTexture, !isAlreadyAttached);
}

static void android_view_HardwareLayer_updateSurfaceTexture(JNIEnv* env, jobject clazz,
        jlong layerUpdaterPtr) {
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerUpdaterPtr);
    layer->updateTexImage();
}

static jlong android_view_HardwareLayer_getLayer(JNIEnv* env, jobject clazz,
        jlong layerUpdaterPtr) {
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerUpdaterPtr);
    return reinterpret_cast<jlong>( layer->backingLayer() );
}

static jint android_view_HardwareLayer_getTexName(JNIEnv* env, jobject clazz,
        jlong layerUpdaterPtr) {
    DeferredLayerUpdater* layer = reinterpret_cast<DeferredLayerUpdater*>(layerUpdaterPtr);
    return layer->backingLayer()->getTexture();
}

#endif // USE_OPENGL_RENDERER

// ----------------------------------------------------------------------------
// JNI Glue
// ----------------------------------------------------------------------------

const char* const kClassPathName = "android/view/HardwareLayer";

static JNINativeMethod gMethods[] = {
#ifdef USE_OPENGL_RENDERER

    { "nOnTextureDestroyed",     "(J)V",       (void*) android_view_HardwareLayer_onTextureDestroyed },

    { "nPrepare",                "(JIIZ)Z",    (void*) android_view_HardwareLayer_prepare },
    { "nSetLayerPaint",          "(JJ)V",      (void*) android_view_HardwareLayer_setLayerPaint },
    { "nSetTransform",           "(JJ)V",      (void*) android_view_HardwareLayer_setTransform },
    { "nSetSurfaceTexture",      "(JLandroid/graphics/SurfaceTexture;Z)V",
            (void*) android_view_HardwareLayer_setSurfaceTexture },
    { "nUpdateSurfaceTexture",   "(J)V",       (void*) android_view_HardwareLayer_updateSurfaceTexture },

    { "nGetLayer",               "(J)J",       (void*) android_view_HardwareLayer_getLayer },
    { "nGetTexName",             "(J)I",       (void*) android_view_HardwareLayer_getTexName },
#endif
};

int register_android_view_HardwareLayer(JNIEnv* env) {
    return AndroidRuntime::registerNativeMethods(env, kClassPathName, gMethods, NELEM(gMethods));
}

};
