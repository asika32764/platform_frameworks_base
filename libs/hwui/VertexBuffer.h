/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ANDROID_HWUI_VERTEX_BUFFER_H
#define ANDROID_HWUI_VERTEX_BUFFER_H


namespace android {
namespace uirenderer {

class VertexBuffer {
public:
    enum Mode {
        kStandard = 0,
        kOnePolyRingShadow = 1,
        kTwoPolyRingShadow = 2
    };

    VertexBuffer()
            : mBuffer(0)
            , mVertexCount(0)
            , mByteCount(0)
            , mMode(kStandard)
            , mCleanupMethod(NULL)
    {}

    ~VertexBuffer() {
        if (mCleanupMethod) mCleanupMethod(mBuffer);
    }

    /**
       This should be the only method used by the Tessellator. Subsequent calls to
       alloc will allocate space within the first allocation (useful if you want to
       eventually allocate multiple regions within a single VertexBuffer, such as
       with PathTessellator::tessellateLines())
     */
    template <class TYPE>
    TYPE* alloc(int vertexCount) {
        if (mVertexCount) {
            TYPE* reallocBuffer = (TYPE*)mReallocBuffer;
            // already have allocated the buffer, re-allocate space within
            if (mReallocBuffer != mBuffer) {
                // not first re-allocation, leave space for degenerate triangles to separate strips
                reallocBuffer += 2;
            }
            mReallocBuffer = reallocBuffer + vertexCount;
            return reallocBuffer;
        }
        mVertexCount = vertexCount;
        mByteCount = mVertexCount * sizeof(TYPE);
        mReallocBuffer = mBuffer = (void*)new TYPE[vertexCount];
        mCleanupMethod = &(cleanup<TYPE>);

        return (TYPE*)mBuffer;
    }

    template <class TYPE>
    void copyInto(const VertexBuffer& srcBuffer, float xOffset, float yOffset) {
        int verticesToCopy = srcBuffer.getVertexCount();

        TYPE* dst = alloc<TYPE>(verticesToCopy);
        TYPE* src = (TYPE*)srcBuffer.getBuffer();

        for (int i = 0; i < verticesToCopy; i++) {
            TYPE::copyWithOffset(&dst[i], src[i], xOffset, yOffset);
        }
    }

    const void* getBuffer() const { return mBuffer; }
    const Rect& getBounds() const { return mBounds; }
    unsigned int getVertexCount() const { return mVertexCount; }
    unsigned int getSize() const { return mByteCount; }
    Mode getMode() const { return mMode; }

    void setBounds(Rect bounds) { mBounds = bounds; }
    void setMode(Mode mode) { mMode = mode; }

    template <class TYPE>
    void createDegenerateSeparators(int allocSize) {
        TYPE* end = (TYPE*)mBuffer + mVertexCount;
        for (TYPE* degen = (TYPE*)mBuffer + allocSize; degen < end; degen += 2 + allocSize) {
            memcpy(degen, degen - 1, sizeof(TYPE));
            memcpy(degen + 1, degen + 2, sizeof(TYPE));
        }
    }

private:
    template <class TYPE>
    static void cleanup(void* buffer) {
        delete[] (TYPE*)buffer;
    }

    Rect mBounds;
    void* mBuffer;
    unsigned int mVertexCount;
    unsigned int mByteCount;
    Mode mMode;

    void* mReallocBuffer; // used for multi-allocation

    void (*mCleanupMethod)(void*);
};

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_VERTEX_BUFFER_H
