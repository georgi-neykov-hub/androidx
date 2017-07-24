/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.arch.navigation;

import android.os.Bundle;
import android.support.annotation.AnimRes;
import android.support.annotation.AnimatorRes;
import android.support.annotation.IdRes;

/**
 * NavOptions stores special options for navigate actions
 */
public class NavOptions {
    static final int LAUNCH_SINGLE_TOP = 0x1;
    static final int LAUNCH_DOCUMENT = 0x2;
    static final int LAUNCH_CLEAR_TASK = 0x4;

    private static final String KEY_LAUNCH_MODE = "launchMode";
    private static final String KEY_POP_UP_TO = "popUpTo";
    private static final String KEY_POP_UP_TO_INCLUSIVE = "popUpToInclusive";
    private static final String KEY_ENTER_ANIM = "enterAnim";
    private static final String KEY_EXIT_ANIM = "exitAnim";

    private int mLaunchMode;
    @IdRes
    private int mPopUpTo;
    private boolean mPopUpToInclusive;
    @AnimRes @AnimatorRes
    private int mEnterAnim;
    @AnimRes @AnimatorRes
    private int mExitAnim;

    NavOptions(int launchMode, @IdRes int popUpTo, boolean popUpToInclusive,
            @AnimRes @AnimatorRes int enterAnim, @AnimRes @AnimatorRes int exitAnim) {
        mLaunchMode = launchMode;
        mPopUpTo = popUpTo;
        mPopUpToInclusive = popUpToInclusive;
        mEnterAnim = enterAnim;
        mExitAnim = exitAnim;
    }

    /**
     * Whether this navigation action should launch as single-top (i.e., there will be at most
     * one copy of a given destination on the top of the back stack).
     * <p>
     * This functions similarly to how {@link android.content.Intent#FLAG_ACTIVITY_SINGLE_TOP}
     * works with activites.
     */
    public boolean shouldLaunchSingleTop() {
        return (mLaunchMode & LAUNCH_SINGLE_TOP) != 0;
    }

    /**
     * Whether this navigation action should launch the destination in a new document.
     * <p>
     * This functions similarly to how {@link android.content.Intent#FLAG_ACTIVITY_NEW_DOCUMENT}
     * works with activites.
     */
    public boolean shouldLaunchDocument() {
        return (mLaunchMode & LAUNCH_DOCUMENT) != 0;
    }

    /**
     * Whether this navigation action should clear the entire back stack
     * <p>
     * This functions similarly to how {@link android.content.Intent#FLAG_ACTIVITY_CLEAR_TASK}
     * works with activites.
     */
    public boolean shouldClearTask() {
        return (mLaunchMode & LAUNCH_CLEAR_TASK) != 0;
    }

    /**
     * The destination to pop up to before navigating. When set, all non-matching destinations
     * should be popped from the back stack.
     * @return the destinationId to pop up to, clearing all intervening destinations
     * @see Builder#setPopUpTo
     * @see #isPopUpToInclusive
     */
    @IdRes
    public int getPopUpTo() {
        return mPopUpTo;
    }

    /**
     * Whether the destination set in {@link #getPopUpTo} should be popped from the back stack.
     * @see Builder#setPopUpTo
     * @see #getPopUpTo
     */
    public boolean isPopUpToInclusive() {
        return mPopUpToInclusive;
    }

    /**
     * The custom enter Animation/Animator that should be run.
     * @return the resource id of a Animation or Animator or -1 if none.
     */
    @AnimRes @AnimatorRes
    public int getEnterAnim() {
        return mEnterAnim;
    }

    /**
     * The custom exit Animation/Animator that should be run.
     * @return the resource id of a Animation or Animator or -1 if none.
     */
    @AnimRes @AnimatorRes
    public int getExitAnim() {
        return mExitAnim;
    }

    Bundle toBundle() {
        Bundle b = new Bundle();
        b.putInt(KEY_LAUNCH_MODE, mLaunchMode);
        b.putInt(KEY_POP_UP_TO, mPopUpTo);
        b.putBoolean(KEY_POP_UP_TO_INCLUSIVE, mPopUpToInclusive);
        b.putInt(KEY_ENTER_ANIM, mEnterAnim);
        b.putInt(KEY_EXIT_ANIM, mExitAnim);
        return b;
    }

    static NavOptions fromBundle(Bundle b) {
        return new NavOptions(b.getInt(KEY_LAUNCH_MODE, 0),
                b.getInt(KEY_POP_UP_TO, 0), b.getBoolean(KEY_POP_UP_TO_INCLUSIVE, false),
                b.getInt(KEY_ENTER_ANIM, -1), b.getInt(KEY_EXIT_ANIM, -1));
    }

    /**
     * Builder for constructing new instances of NavOptions.
     */
    public static class Builder {
        int mLaunchMode;
        @IdRes
        int mPopUpTo;
        boolean mPopUpToInclusive;
        @AnimRes @AnimatorRes
        int mEnterAnim = -1;
        @AnimRes @AnimatorRes
        int mExitAnim = -1;

        public Builder() {
        }

        /**
         * Launch a navigation target as single-top if you are making a lateral navigation
         * between instances of the same target (e.g. detail pages about similar data items)
         * that should not preserve history.
         *
         * @param singleTop true to launch as single-top
         */
        public Builder setLaunchSingleTop(boolean singleTop) {
            if (singleTop) {
                mLaunchMode |= LAUNCH_SINGLE_TOP;
            } else {
                mLaunchMode &= ~LAUNCH_SINGLE_TOP;
            }
            return this;
        }

        /**
         * Launch a navigation target as a document if you want it to appear as its own
         * entry in the system Overview screen. If the same document is launched multiple times
         * it will not create a new task, it will bring the existing document task to the front.
         *
         * <p>If the user presses the system Back key from a new document task they will land
         * on their previous task. If the user reached the document task from the system Overview
         * screen they will be taken to their home screen.</p>
         *
         * @param launchDocument true to launch a new document task
         */
        public Builder setLaunchDocument(boolean launchDocument) {
            if (launchDocument) {
                mLaunchMode |= LAUNCH_DOCUMENT;
            } else {
                mLaunchMode &= ~LAUNCH_DOCUMENT;
            }
            return this;
        }

        /**
         * Clear the entire task before launching this target. If you are launching as a
         * {@link #setLaunchDocument(boolean) document}, this will clear the document task.
         * Otherwise it will clear the current task.
         *
         * @param clearTask
         * @return
         */
        public Builder setClearTask(boolean clearTask) {
            if (clearTask) {
                mLaunchMode |= LAUNCH_CLEAR_TASK;
            } else {
                mLaunchMode &= ~LAUNCH_CLEAR_TASK;
            }
            return this;
        }

        /**
         * Pop up to a given destination before navigating. This pops all non-matching destinations
         * from the back stack until this destination is found.
         *
         * @param destinationId The destination to pop up to, clearing all intervening destinations.
         * @param inclusive true to also pop the given destination from the back stack.
         * @return this Builder
         * @see NavOptions#getPopUpTo
         * @see NavOptions#isPopUpToInclusive
         */
        public Builder setPopUpTo(@IdRes int destinationId, boolean inclusive) {
            mPopUpTo = destinationId;
            mPopUpToInclusive = inclusive;
            return this;
        }

        /**
         * Sets a custom Animation or Animator resource for the enter animation.
         *
         * <p>Note: Animator resources are not supported for navigating to a new Activity</p>
         * @param enterAnim Custom animation to run
         * @return this Builder
         * @see NavOptions#getEnterAnim()
         */
        public Builder setEnterAnim(@AnimRes @AnimatorRes int enterAnim) {
            mEnterAnim = enterAnim;
            return this;
        }

        /**
         * Sets a custom Animation or Animator resource for the exit animation.
         *
         * <p>Note: Animator resources are not supported for navigating to a new Activity</p>
         * @param exitAnim Custom animation to run
         * @return this Builder
         * @see NavOptions#getExitAnim()
         */
        public Builder setExitAnim(@AnimRes @AnimatorRes int exitAnim) {
            mExitAnim = exitAnim;
            return this;
        }

        /**
         * @return a constructed NavOptions
         */
        public NavOptions build() {
            return new NavOptions(mLaunchMode, mPopUpTo, mPopUpToInclusive, mEnterAnim, mExitAnim);
        }
    }
}
