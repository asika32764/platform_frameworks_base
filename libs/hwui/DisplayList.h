/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef ANDROID_HWUI_DISPLAY_LIST_H
#define ANDROID_HWUI_DISPLAY_LIST_H

#ifndef LOG_TAG
    #define LOG_TAG "OpenGLRenderer"
#endif

#include <SkCamera.h>
#include <SkMatrix.h>

#include <private/hwui/DrawGlInfo.h>

#include <utils/KeyedVector.h>
#include <utils/LinearAllocator.h>
#include <utils/RefBase.h>
#include <utils/SortedVector.h>
#include <utils/String8.h>
#include <utils/Vector.h>

#include <cutils/compiler.h>

#include <androidfw/ResourceTypes.h>

#include "Debug.h"
#include "Matrix.h"
#include "DeferredDisplayList.h"
#include "RenderProperties.h"

class SkBitmap;
class SkPaint;
class SkPath;
class SkRegion;

namespace android {
namespace uirenderer {

class DeferredDisplayList;
class DisplayListOp;
class DisplayListRenderer;
class OpenGLRenderer;
class Rect;
class Layer;

class ClipRectOp;
class SaveLayerOp;
class SaveOp;
class RestoreToCountOp;
class DrawDisplayListOp;

/**
 * Holds data used in the playback a tree of DisplayLists.
 */
class PlaybackStateStruct {
protected:
    PlaybackStateStruct(OpenGLRenderer& renderer, int replayFlags, LinearAllocator* allocator)
            : mRenderer(renderer), mReplayFlags(replayFlags), mAllocator(allocator){}

public:
    OpenGLRenderer& mRenderer;
    const int mReplayFlags;

    // Allocator with the lifetime of a single frame.
    // replay uses an Allocator owned by the struct, while defer shares the DeferredDisplayList's Allocator
    LinearAllocator * const mAllocator;
};

class DeferStateStruct : public PlaybackStateStruct {
public:
    DeferStateStruct(DeferredDisplayList& deferredList, OpenGLRenderer& renderer, int replayFlags)
            : PlaybackStateStruct(renderer, replayFlags, &(deferredList.mAllocator)),
            mDeferredList(deferredList) {}

    DeferredDisplayList& mDeferredList;
};

class ReplayStateStruct : public PlaybackStateStruct {
public:
    ReplayStateStruct(OpenGLRenderer& renderer, Rect& dirty, int replayFlags)
            : PlaybackStateStruct(renderer, replayFlags, &mReplayAllocator),
            mDirty(dirty), mDrawGlStatus(DrawGlInfo::kStatusDone) {}

    Rect& mDirty;
    status_t mDrawGlStatus;
    LinearAllocator mReplayAllocator;
};

/**
 * Data structure that holds the list of commands used in display list stream
 */
class DisplayListData {
public:
    DisplayListData();
    ~DisplayListData();

    // allocator into which all ops were allocated
    LinearAllocator allocator;

    // pointers to all ops within display list, pointing into allocator data
    Vector<DisplayListOp*> displayListOps;

    // index of DisplayListOp restore, after which projected descendents should be drawn
    int projectionReceiveIndex;

    Vector<const SkBitmap*> bitmapResources;
    Vector<const SkBitmap*> ownedBitmapResources;
    Vector<const Res_png_9patch*> patchResources;

    Vector<const SkPaint*> paints;
    Vector<const SkPath*> paths;
    SortedVector<const SkPath*> sourcePaths;
    Vector<const SkRegion*> regions;
    Vector<Layer*> layers;
    uint32_t functorCount;
    bool hasDrawOps;

    bool isEmpty() {
        return !displayListOps.size();
    }

    void addChild(DrawDisplayListOp* childOp);
    const Vector<DrawDisplayListOp*>& children() { return mChildren; }

    void refProperty(CanvasPropertyPrimitive* prop) {
        mReferenceHolders.push(prop);
    }

    void refProperty(CanvasPropertyPaint* prop) {
        mReferenceHolders.push(prop);
    }

private:
    Vector< sp<VirtualLightRefBase> > mReferenceHolders;

    // list of children display lists for quick, non-drawing traversal
    Vector<DrawDisplayListOp*> mChildren;

    void cleanupResources();
};

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_OPENGL_RENDERER_H
