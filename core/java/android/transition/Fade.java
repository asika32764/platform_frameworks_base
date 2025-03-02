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

package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * This transition tracks changes to the visibility of target views in the
 * start and end scenes and fades views in or out when they become visible
 * or non-visible. Visibility is determined by both the
 * {@link View#setVisibility(int)} state of the view as well as whether it
 * is parented in the current view hierarchy.
 *
 * <p>The ability of this transition to fade out a particular view, and the
 * way that that fading operation takes place, is based on
 * the situation of the view in the view hierarchy. For example, if a view was
 * simply removed from its parent, then the view will be added into a {@link
 * android.view.ViewGroupOverlay} while fading. If a visible view is
 * changed to be {@link View#GONE} or {@link View#INVISIBLE}, then the
 * visibility will be changed to {@link View#VISIBLE} for the duration of
 * the animation. However, if a view is in a hierarchy which is also altering
 * its visibility, the situation can be more complicated. In general, if a
 * view that is no longer in the hierarchy in the end scene still has a
 * parent (so its parent hierarchy was removed, but it was not removed from
 * its parent), then it will be left alone to avoid side-effects from
 * improperly removing it from its parent. The only exception to this is if
 * the previous {@link Scene} was
 * {@link Scene#getSceneForLayout(android.view.ViewGroup, int, android.content.Context)
 * created from a layout resource file}, then it is considered safe to un-parent
 * the starting scene view in order to fade it out.</p>
 *
 * <p>A Fade transition can be described in a resource file by using the
 * tag <code>fade</code>, along with the standard
 * attributes of {@link android.R.styleable#Fade} and
 * {@link android.R.styleable#Transition}.</p>

 */
public class Fade extends Visibility {

    private static boolean DBG = Transition.DBG && false;

    private static final String LOG_TAG = "Fade";

    /**
     * Fading mode used in {@link #Fade(int)} to make the transition
     * operate on targets that are appearing. Maybe be combined with
     * {@link #OUT} to fade both in and out.
     */
    public static final int IN = 0x1;
    /**
     * Fading mode used in {@link #Fade(int)} to make the transition
     * operate on targets that are disappearing. Maybe be combined with
     * {@link #IN} to fade both in and out.
     */
    public static final int OUT = 0x2;

    private int mFadingMode;

    /**
     * Constructs a Fade transition that will fade targets in and out.
     */
    public Fade() {
        this(IN | OUT);
    }

    /**
     * Constructs a Fade transition that will fade targets in
     * and/or out, according to the value of fadingMode.
     *
     * @param fadingMode The behavior of this transition, a combination of
     * {@link #IN} and {@link #OUT}.
     */
    public Fade(int fadingMode) {
        mFadingMode = fadingMode;
    }

    /**
     * Utility method to handle creating and running the Animator.
     */
    private Animator createAnimation(View view, float startAlpha, float endAlpha) {
        if (startAlpha == endAlpha) {
            return null;
        }
        view.setTransitionAlpha(startAlpha);
        final ObjectAnimator anim = ObjectAnimator.ofFloat(view, "transitionAlpha", endAlpha);
        if (DBG) {
            Log.d(LOG_TAG, "Created animator " + anim);
        }
        FadeAnimatorListener listener = new FadeAnimatorListener(view);
        anim.addListener(listener);
        anim.addPauseListener(listener);
        return anim;
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        if ((mFadingMode & IN) != IN || endValues == null) {
            return null;
        }
        if (DBG) {
            View startView = (startValues != null) ? startValues.view : null;
            Log.d(LOG_TAG, "Fade.onAppear: startView, startVis, endView, endVis = " +
                    startView + ", " + view);
        }
        return createAnimation(view, 0, 1);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, final View view, TransitionValues startValues,
            TransitionValues endValues) {
        if ((mFadingMode & OUT) != OUT) {
            return null;
        }

        return createAnimation(view, 1, 0);
    }

    private static class FadeAnimatorListener extends AnimatorListenerAdapter {
        private final View mView;
        private boolean mCanceled = false;
        private float mPausedAlpha = -1;

        public FadeAnimatorListener(View view) {
            mView = view;
        }

        @Override
        public void onAnimationCancel(Animator animator) {
            mCanceled = true;
            if (mPausedAlpha >= 0) {
                mView.setTransitionAlpha(mPausedAlpha);
            }
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            if (!mCanceled) {
                mView.setTransitionAlpha(1);
            }
        }

        @Override
        public void onAnimationPause(Animator animator) {
            mPausedAlpha = mView.getTransitionAlpha();
            mView.setTransitionAlpha(1);
        }

        @Override
        public void onAnimationResume(Animator animator) {
            mView.setTransitionAlpha(mPausedAlpha);
        }
    }
}
