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

package android.animation;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.Log;
import android.util.Property;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * This subclass of {@link ValueAnimator} provides support for animating properties on target objects.
 * The constructors of this class take parameters to define the target object that will be animated
 * as well as the name of the property that will be animated. Appropriate set/get functions
 * are then determined internally and the animation will call these functions as necessary to
 * animate the property.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about animating with {@code ObjectAnimator}, read the
 * <a href="{@docRoot}guide/topics/graphics/prop-animation.html#object-animator">Property
 * Animation</a> developer guide.</p>
 * </div>
 *
 * @see #setPropertyName(String)
 *
 */
public final class ObjectAnimator extends ValueAnimator {
    private static final String LOG_TAG = "ObjectAnimator";

    private static final boolean DBG = false;

    /**
     * A weak reference to the target object on which the property exists, set
     * in the constructor. We'll cancel the animation if this goes away.
     */
    private WeakReference<Object> mTarget;

    private String mPropertyName;

    private Property mProperty;

    private boolean mAutoCancel = false;

    /**
     * Sets the name of the property that will be animated. This name is used to derive
     * a setter function that will be called to set animated values.
     * For example, a property name of <code>foo</code> will result
     * in a call to the function <code>setFoo()</code> on the target object. If either
     * <code>valueFrom</code> or <code>valueTo</code> is null, then a getter function will
     * also be derived and called.
     *
     * <p>For best performance of the mechanism that calls the setter function determined by the
     * name of the property being animated, use <code>float</code> or <code>int</code> typed values,
     * and make the setter function for those properties have a <code>void</code> return value. This
     * will cause the code to take an optimized path for these constrained circumstances. Other
     * property types and return types will work, but will have more overhead in processing
     * the requests due to normal reflection mechanisms.</p>
     *
     * <p>Note that the setter function derived from this property name
     * must take the same parameter type as the
     * <code>valueFrom</code> and <code>valueTo</code> properties, otherwise the call to
     * the setter function will fail.</p>
     *
     * <p>If this ObjectAnimator has been set up to animate several properties together,
     * using more than one PropertyValuesHolder objects, then setting the propertyName simply
     * sets the propertyName in the first of those PropertyValuesHolder objects.</p>
     *
     * @param propertyName The name of the property being animated. Should not be null.
     */
    public void setPropertyName(@NonNull String propertyName) {
        // mValues could be null if this is being constructed piecemeal. Just record the
        // propertyName to be used later when setValues() is called if so.
        if (mValues != null) {
            PropertyValuesHolder valuesHolder = mValues[0];
            String oldName = valuesHolder.getPropertyName();
            valuesHolder.setPropertyName(propertyName);
            mValuesMap.remove(oldName);
            mValuesMap.put(propertyName, valuesHolder);
        }
        mPropertyName = propertyName;
        // New property/values/target should cause re-initialization prior to starting
        mInitialized = false;
    }

    /**
     * Sets the property that will be animated. Property objects will take precedence over
     * properties specified by the {@link #setPropertyName(String)} method. Animations should
     * be set up to use one or the other, not both.
     *
     * @param property The property being animated. Should not be null.
     */
    public void setProperty(@NonNull Property property) {
        // mValues could be null if this is being constructed piecemeal. Just record the
        // propertyName to be used later when setValues() is called if so.
        if (mValues != null) {
            PropertyValuesHolder valuesHolder = mValues[0];
            String oldName = valuesHolder.getPropertyName();
            valuesHolder.setProperty(property);
            mValuesMap.remove(oldName);
            mValuesMap.put(mPropertyName, valuesHolder);
        }
        if (mProperty != null) {
            mPropertyName = property.getName();
        }
        mProperty = property;
        // New property/values/target should cause re-initialization prior to starting
        mInitialized = false;
    }

    /**
     * Gets the name of the property that will be animated. This name will be used to derive
     * a setter function that will be called to set animated values.
     * For example, a property name of <code>foo</code> will result
     * in a call to the function <code>setFoo()</code> on the target object. If either
     * <code>valueFrom</code> or <code>valueTo</code> is null, then a getter function will
     * also be derived and called.
     *
     * <p>If this animator was created with a {@link Property} object instead of the
     * string name of a property, then this method will return the {@link
     * Property#getName() name} of that Property object instead. If this animator was
     * created with one or more {@link PropertyValuesHolder} objects, then this method
     * will return the {@link PropertyValuesHolder#getPropertyName() name} of that
     * object (if there was just one) or a comma-separated list of all of the
     * names (if there are more than one).</p>
     */
    @Nullable
    public String getPropertyName() {
        String propertyName = null;
        if (mPropertyName != null) {
            propertyName = mPropertyName;
        } else if (mProperty != null) {
            propertyName = mProperty.getName();
        } else if (mValues != null && mValues.length > 0) {
            for (int i = 0; i < mValues.length; ++i) {
                if (i == 0) {
                    propertyName = "";
                } else {
                    propertyName += ",";
                }
                propertyName += mValues[i].getPropertyName();
            }
        }
        return propertyName;
    }

    @Override
    String getNameForTrace() {
        return "animator:" + getPropertyName();
    }

    /**
     * Creates a new ObjectAnimator object. This default constructor is primarily for
     * use internally; the other constructors which take parameters are more generally
     * useful.
     */
    public ObjectAnimator() {
    }

    /**
     * Private utility constructor that initializes the target object and name of the
     * property being animated.
     *
     * @param target The object whose property is to be animated. This object should
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter.
     * @param propertyName The name of the property being animated.
     */
    private ObjectAnimator(Object target, String propertyName) {
        setTarget(target);
        setPropertyName(propertyName);
    }

    /**
     * Private utility constructor that initializes the target object and property being animated.
     *
     * @param target The object whose property is to be animated.
     * @param property The property being animated.
     */
    private <T> ObjectAnimator(T target, Property<T, ?> property) {
        setTarget(target);
        setProperty(property);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between int values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation).
     *
     * @param target The object whose property is to be animated. This object should
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter.
     * @param propertyName The name of the property being animated.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static ObjectAnimator ofInt(Object target, String propertyName, int... values) {
        ObjectAnimator anim = new ObjectAnimator(target, propertyName);
        anim.setIntValues(values);
        return anim;
    }

    /**
     * Constructs and returns an ObjectAnimator that animates coordinates along a <code>Path</code>
     * using two properties. A <code>Path</code></> animation moves in two dimensions, animating
     * coordinates <code>(x, y)</code> together to follow the line. In this variation, the
     * coordinates are integers that are set to separate properties designated by
     * <code>xPropertyName</code> and <code>yPropertyName</code>.
     *
     * @param target The object whose properties are to be animated. This object should
     *               have public methods on it called <code>setNameX()</code> and
     *               <code>setNameY</code>, where <code>nameX</code> and <code>nameY</code>
     *               are the value of <code>xPropertyName</code> and <code>yPropertyName</code>
     *               parameters, respectively.
     * @param xPropertyName The name of the property for the x coordinate being animated.
     * @param yPropertyName The name of the property for the y coordinate being animated.
     * @param path The <code>Path</code> to animate values along.
     * @return An ObjectAnimator object that is set up to animate along <code>path</code>.
     */
    public static ObjectAnimator ofInt(Object target, String xPropertyName, String yPropertyName,
            Path path) {
        Keyframe[][] keyframes = PropertyValuesHolder.createKeyframes(path, true);
        PropertyValuesHolder x = PropertyValuesHolder.ofKeyframe(xPropertyName, keyframes[0]);
        PropertyValuesHolder y = PropertyValuesHolder.ofKeyframe(yPropertyName, keyframes[1]);
        return ofPropertyValuesHolder(target, x, y);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between int values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation).
     *
     * @param target The object whose property is to be animated.
     * @param property The property being animated.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static <T> ObjectAnimator ofInt(T target, Property<T, Integer> property, int... values) {
        ObjectAnimator anim = new ObjectAnimator(target, property);
        anim.setIntValues(values);
        return anim;
    }

    /**
     * Constructs and returns an ObjectAnimator that animates coordinates along a <code>Path</code>
     * using two properties.  A <code>Path</code></> animation moves in two dimensions, animating
     * coordinates <code>(x, y)</code> together to follow the line. In this variation, the
     * coordinates are integers that are set to separate properties, <code>xProperty</code> and
     * <code>yProperty</code>.
     *
     * @param target The object whose properties are to be animated.
     * @param xProperty The property for the x coordinate being animated.
     * @param yProperty The property for the y coordinate being animated.
     * @param path The <code>Path</code> to animate values along.
     * @return An ObjectAnimator object that is set up to animate along <code>path</code>.
     */
    public static <T> ObjectAnimator ofInt(T target, Property<T, Integer> xProperty,
            Property<T, Integer> yProperty, Path path) {
        Keyframe[][] keyframes = PropertyValuesHolder.createKeyframes(path, true);
        PropertyValuesHolder x = PropertyValuesHolder.ofKeyframe(xProperty, keyframes[0]);
        PropertyValuesHolder y = PropertyValuesHolder.ofKeyframe(yProperty, keyframes[1]);
        return ofPropertyValuesHolder(target, x, y);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates over int values for a multiple
     * parameters setter. Only public methods that take only int parameters are supported.
     * Each <code>int[]</code> contains a complete set of parameters to the setter method.
     * At least two <code>int[]</code> values must be provided, a start and end. More than two
     * values imply a starting value, values to animate through along the way, and an ending
     * value (these values will be distributed evenly across the duration of the animation).
     *
     * @param target The object whose property is to be animated. This object may
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter. <code>propertyName</code> may also
     * be the case-sensitive complete name of the public setter method.
     * @param propertyName The name of the property being animated or the name of the setter method.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static ObjectAnimator ofMultiInt(Object target, String propertyName, int[][] values) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiInt(propertyName, values);
        return ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates the target using a multi-int setter
     * along the given <code>Path</code>. A <code>Path</code></> animation moves in two dimensions,
     * animating coordinates <code>(x, y)</code> together to follow the line. In this variation, the
     * coordinates are integer x and y coordinates used in the first and second parameter of the
     * setter, respectively.
     *
     * @param target The object whose property is to be animated. This object may
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter. <code>propertyName</code> may also
     * be the case-sensitive complete name of the public setter method.
     * @param propertyName The name of the property being animated or the name of the setter method.
     * @param path The <code>Path</code> to animate values along.
     * @return An ObjectAnimator object that is set up to animate along <code>path</code>.
     */
    public static ObjectAnimator ofMultiInt(Object target, String propertyName, Path path) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiInt(propertyName, path);
        return ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates over values for a multiple int
     * parameters setter. Only public methods that take only int parameters are supported.
     * <p>At least two values must be provided, a start and end. More than two
     * values imply a starting value, values to animate through along the way, and an ending
     * value (these values will be distributed evenly across the duration of the animation).</p>
     *
     * @param target The object whose property is to be animated. This object may
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter. <code>propertyName</code> may also
     * be the case-sensitive complete name of the public setter method.
     * @param propertyName The name of the property being animated or the name of the setter method.
     * @param converter Converts T objects into int parameters for the multi-value setter.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static <T> ObjectAnimator ofMultiInt(Object target, String propertyName,
            TypeConverter<T, int[]> converter, TypeEvaluator<T> evaluator, T... values) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiInt(propertyName, converter,
                evaluator, values);
        return ObjectAnimator.ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between color values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation).
     *
     * @param target The object whose property is to be animated. This object should
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter.
     * @param propertyName The name of the property being animated.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static ObjectAnimator ofArgb(Object target, String propertyName, int... values) {
        ObjectAnimator animator = ofInt(target, propertyName, values);
        animator.setEvaluator(ArgbEvaluator.getInstance());
        return animator;
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between color values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation).
     *
     * @param target The object whose property is to be animated.
     * @param property The property being animated.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static <T> ObjectAnimator ofArgb(T target, Property<T, Integer> property,
            int... values) {
        ObjectAnimator animator = ofInt(target, property, values);
        animator.setEvaluator(ArgbEvaluator.getInstance());
        return animator;
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between float values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation).
     *
     * @param target The object whose property is to be animated. This object should
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter.
     * @param propertyName The name of the property being animated.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static ObjectAnimator ofFloat(Object target, String propertyName, float... values) {
        ObjectAnimator anim = new ObjectAnimator(target, propertyName);
        anim.setFloatValues(values);
        return anim;
    }

    /**
     * Constructs and returns an ObjectAnimator that animates coordinates along a <code>Path</code>
     * using two properties. A <code>Path</code></> animation moves in two dimensions, animating
     * coordinates <code>(x, y)</code> together to follow the line. In this variation, the
     * coordinates are floats that are set to separate properties designated by
     * <code>xPropertyName</code> and <code>yPropertyName</code>.
     *
     * @param target The object whose properties are to be animated. This object should
     *               have public methods on it called <code>setNameX()</code> and
     *               <code>setNameY</code>, where <code>nameX</code> and <code>nameY</code>
     *               are the value of the <code>xPropertyName</code> and <code>yPropertyName</code>
     *               parameters, respectively.
     * @param xPropertyName The name of the property for the x coordinate being animated.
     * @param yPropertyName The name of the property for the y coordinate being animated.
     * @param path The <code>Path</code> to animate values along.
     * @return An ObjectAnimator object that is set up to animate along <code>path</code>.
     */
    public static ObjectAnimator ofFloat(Object target, String xPropertyName, String yPropertyName,
            Path path) {
        Keyframe[][] keyframes = PropertyValuesHolder.createKeyframes(path, false);
        PropertyValuesHolder x = PropertyValuesHolder.ofKeyframe(xPropertyName, keyframes[0]);
        PropertyValuesHolder y = PropertyValuesHolder.ofKeyframe(yPropertyName, keyframes[1]);
        return ofPropertyValuesHolder(target, x, y);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between float values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation).
     *
     * @param target The object whose property is to be animated.
     * @param property The property being animated.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static <T> ObjectAnimator ofFloat(T target, Property<T, Float> property,
            float... values) {
        ObjectAnimator anim = new ObjectAnimator(target, property);
        anim.setFloatValues(values);
        return anim;
    }

    /**
     * Constructs and returns an ObjectAnimator that animates coordinates along a <code>Path</code>
     * using two properties. A <code>Path</code></> animation moves in two dimensions, animating
     * coordinates <code>(x, y)</code> together to follow the line. In this variation, the
     * coordinates are floats that are set to separate properties, <code>xProperty</code> and
     * <code>yProperty</code>.
     *
     * @param target The object whose properties are to be animated.
     * @param xProperty The property for the x coordinate being animated.
     * @param yProperty The property for the y coordinate being animated.
     * @param path The <code>Path</code> to animate values along.
     * @return An ObjectAnimator object that is set up to animate along <code>path</code>.
     */
    public static <T> ObjectAnimator ofFloat(T target, Property<T, Float> xProperty,
            Property<T, Float> yProperty, Path path) {
        return ofFloat(target, xProperty.getName(), yProperty.getName(), path);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates over float values for a multiple
     * parameters setter. Only public methods that take only float parameters are supported.
     * Each <code>float[]</code> contains a complete set of parameters to the setter method.
     * At least two <code>float[]</code> values must be provided, a start and end. More than two
     * values imply a starting value, values to animate through along the way, and an ending
     * value (these values will be distributed evenly across the duration of the animation).
     *
     * @param target The object whose property is to be animated. This object may
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter. <code>propertyName</code> may also
     * be the case-sensitive complete name of the public setter method.
     * @param propertyName The name of the property being animated or the name of the setter method.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static ObjectAnimator ofMultiFloat(Object target, String propertyName,
            float[][] values) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiFloat(propertyName, values);
        return ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates the target using a multi-float setter
     * along the given <code>Path</code>. A <code>Path</code></> animation moves in two dimensions,
     * animating coordinates <code>(x, y)</code> together to follow the line. In this variation, the
     * coordinates are float x and y coordinates used in the first and second parameter of the
     * setter, respectively.
     *
     * @param target The object whose property is to be animated. This object may
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter. <code>propertyName</code> may also
     * be the case-sensitive complete name of the public setter method.
     * @param propertyName The name of the property being animated or the name of the setter method.
     * @param path The <code>Path</code> to animate values along.
     * @return An ObjectAnimator object that is set up to animate along <code>path</code>.
     */
    public static ObjectAnimator ofMultiFloat(Object target, String propertyName, Path path) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiFloat(propertyName, path);
        return ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates over values for a multiple float
     * parameters setter. Only public methods that take only float parameters are supported.
     * <p>At least two values must be provided, a start and end. More than two
     * values imply a starting value, values to animate through along the way, and an ending
     * value (these values will be distributed evenly across the duration of the animation).</p>
     *
     * @param target The object whose property is to be animated. This object may
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter. <code>propertyName</code> may also
     * be the case-sensitive complete name of the public setter method.
     * @param propertyName The name of the property being animated or the name of the setter method.
     * @param converter Converts T objects into float parameters for the multi-value setter.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static <T> ObjectAnimator ofMultiFloat(Object target, String propertyName,
            TypeConverter<T, float[]> converter, TypeEvaluator<T> evaluator, T... values) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofMultiFloat(propertyName, converter,
                evaluator, values);
        return ObjectAnimator.ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between Object values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation).
     *
     * @param target The object whose property is to be animated. This object should
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter.
     * @param propertyName The name of the property being animated.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    public static ObjectAnimator ofObject(Object target, String propertyName,
            TypeEvaluator evaluator, Object... values) {
        ObjectAnimator anim = new ObjectAnimator(target, propertyName);
        anim.setObjectValues(values);
        anim.setEvaluator(evaluator);
        return anim;
    }

    /**
     * Constructs and returns an ObjectAnimator that animates a property along a <code>Path</code>.
     * A <code>Path</code></> animation moves in two dimensions, animating coordinates
     * <code>(x, y)</code> together to follow the line. This variant animates the coordinates
     * in a <code>PointF</code> to follow the <code>Path</code>. If the <code>Property</code>
     * associated with <code>propertyName</code> uses a type other than <code>PointF</code>,
     * <code>converter</code> can be used to change from <code>PointF</code> to the type
     * associated with the <code>Property</code>.
     *
     * @param target The object whose property is to be animated. This object should
     * have a public method on it called <code>setName()</code>, where <code>name</code> is
     * the value of the <code>propertyName</code> parameter.
     * @param propertyName The name of the property being animated.
     * @param converter Converts a PointF to the type associated with the setter. May be
     *                  null if conversion is unnecessary.
     * @param path The <code>Path</code> to animate values along.
     * @return An ObjectAnimator object that is set up to animate along <code>path</code>.
     */
    @NonNull
    public static ObjectAnimator ofObject(Object target, String propertyName,
            @Nullable TypeConverter<PointF, ?> converter, Path path) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofObject(propertyName, converter, path);
        return ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between Object values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation).
     *
     * @param target The object whose property is to be animated.
     * @param property The property being animated.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    @NonNull
    public static <T, V> ObjectAnimator ofObject(T target, Property<T, V> property,
            TypeEvaluator<V> evaluator, V... values) {
        ObjectAnimator anim = new ObjectAnimator(target, property);
        anim.setObjectValues(values);
        anim.setEvaluator(evaluator);
        return anim;
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between Object values. A single
     * value implies that that value is the one being animated to. Two values imply starting
     * and ending values. More than two values imply a starting value, values to animate through
     * along the way, and an ending value (these values will be distributed evenly across
     * the duration of the animation). This variant supplies a <code>TypeConverter</code> to
     * convert from the animated values to the type of the property. If only one value is
     * supplied, the <code>TypeConverter</code> must be a
     * {@link android.animation.BidirectionalTypeConverter} to retrieve the current value.
     *
     * @param target The object whose property is to be animated.
     * @param property The property being animated.
     * @param converter Converts the animated object to the Property type.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values A set of values that the animation will animate between over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    @NonNull
    public static <T, V, P> ObjectAnimator ofObject(T target, Property<T, P> property,
            TypeConverter<V, P> converter, TypeEvaluator<V> evaluator, V... values) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofObject(property, converter, evaluator,
                values);
        return ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates a property along a <code>Path</code>.
     * A <code>Path</code></> animation moves in two dimensions, animating coordinates
     * <code>(x, y)</code> together to follow the line. This variant animates the coordinates
     * in a <code>PointF</code> to follow the <code>Path</code>. If <code>property</code>
     * uses a type other than <code>PointF</code>, <code>converter</code> can be used to change
     * from <code>PointF</code> to the type associated with the <code>Property</code>.
     *
     * @param target The object whose property is to be animated.
     * @param property The property being animated. Should not be null.
     * @param converter Converts a PointF to the type associated with the setter. May be
     *                  null if conversion is unnecessary.
     * @param path The <code>Path</code> to animate values along.
     * @return An ObjectAnimator object that is set up to animate along <code>path</code>.
     */
    @NonNull
    public static <T, V> ObjectAnimator ofObject(T target, @NonNull Property<T, V> property,
            @Nullable TypeConverter<PointF, V> converter, Path path) {
        PropertyValuesHolder pvh = PropertyValuesHolder.ofObject(property, converter, path);
        return ofPropertyValuesHolder(target, pvh);
    }

    /**
     * Constructs and returns an ObjectAnimator that animates between the sets of values specified
     * in <code>PropertyValueHolder</code> objects. This variant should be used when animating
     * several properties at once with the same ObjectAnimator, since PropertyValuesHolder allows
     * you to associate a set of animation values with a property name.
     *
     * @param target The object whose property is to be animated. Depending on how the
     * PropertyValuesObjects were constructed, the target object should either have the {@link
     * android.util.Property} objects used to construct the PropertyValuesHolder objects or (if the
     * PropertyValuesHOlder objects were created with property names) the target object should have
     * public methods on it called <code>setName()</code>, where <code>name</code> is the name of
     * the property passed in as the <code>propertyName</code> parameter for each of the
     * PropertyValuesHolder objects.
     * @param values A set of PropertyValuesHolder objects whose values will be animated between
     * over time.
     * @return An ObjectAnimator object that is set up to animate between the given values.
     */
    @NonNull
    public static ObjectAnimator ofPropertyValuesHolder(Object target,
            PropertyValuesHolder... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(values);
        return anim;
    }

    @Override
    public void setIntValues(int... values) {
        if (mValues == null || mValues.length == 0) {
            // No values yet - this animator is being constructed piecemeal. Init the values with
            // whatever the current propertyName is
            if (mProperty != null) {
                setValues(PropertyValuesHolder.ofInt(mProperty, values));
            } else {
                setValues(PropertyValuesHolder.ofInt(mPropertyName, values));
            }
        } else {
            super.setIntValues(values);
        }
    }

    @Override
    public void setFloatValues(float... values) {
        if (mValues == null || mValues.length == 0) {
            // No values yet - this animator is being constructed piecemeal. Init the values with
            // whatever the current propertyName is
            if (mProperty != null) {
                setValues(PropertyValuesHolder.ofFloat(mProperty, values));
            } else {
                setValues(PropertyValuesHolder.ofFloat(mPropertyName, values));
            }
        } else {
            super.setFloatValues(values);
        }
    }

    @Override
    public void setObjectValues(Object... values) {
        if (mValues == null || mValues.length == 0) {
            // No values yet - this animator is being constructed piecemeal. Init the values with
            // whatever the current propertyName is
            if (mProperty != null) {
                setValues(PropertyValuesHolder.ofObject(mProperty, (TypeEvaluator) null, values));
            } else {
                setValues(PropertyValuesHolder.ofObject(mPropertyName,
                        (TypeEvaluator) null, values));
            }
        } else {
            super.setObjectValues(values);
        }
    }

    /**
     * autoCancel controls whether an ObjectAnimator will be canceled automatically
     * when any other ObjectAnimator with the same target and properties is started.
     * Setting this flag may make it easier to run different animators on the same target
     * object without having to keep track of whether there are conflicting animators that
     * need to be manually canceled. Canceling animators must have the same exact set of
     * target properties, in the same order.
     *
     * @param cancel Whether future ObjectAnimators with the same target and properties
     * as this ObjectAnimator will cause this ObjectAnimator to be canceled.
     */
    public void setAutoCancel(boolean cancel) {
        mAutoCancel = cancel;
    }

    private boolean hasSameTargetAndProperties(@Nullable Animator anim) {
        if (anim instanceof ObjectAnimator) {
            PropertyValuesHolder[] theirValues = ((ObjectAnimator) anim).getValues();
            if (((ObjectAnimator) anim).getTarget() == getTarget() &&
                    mValues.length == theirValues.length) {
                for (int i = 0; i < mValues.length; ++i) {
                    PropertyValuesHolder pvhMine = mValues[i];
                    PropertyValuesHolder pvhTheirs = theirValues[i];
                    if (pvhMine.getPropertyName() == null ||
                            !pvhMine.getPropertyName().equals(pvhTheirs.getPropertyName())) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        // See if any of the current active/pending animators need to be canceled
        AnimationHandler handler = sAnimationHandler.get();
        if (handler != null) {
            int numAnims = handler.mAnimations.size();
            for (int i = numAnims - 1; i >= 0; i--) {
                if (handler.mAnimations.get(i) instanceof ObjectAnimator) {
                    ObjectAnimator anim = (ObjectAnimator) handler.mAnimations.get(i);
                    if (anim.mAutoCancel && hasSameTargetAndProperties(anim)) {
                        anim.cancel();
                    }
                }
            }
            numAnims = handler.mPendingAnimations.size();
            for (int i = numAnims - 1; i >= 0; i--) {
                if (handler.mPendingAnimations.get(i) instanceof ObjectAnimator) {
                    ObjectAnimator anim = (ObjectAnimator) handler.mPendingAnimations.get(i);
                    if (anim.mAutoCancel && hasSameTargetAndProperties(anim)) {
                        anim.cancel();
                    }
                }
            }
            numAnims = handler.mDelayedAnims.size();
            for (int i = numAnims - 1; i >= 0; i--) {
                if (handler.mDelayedAnims.get(i) instanceof ObjectAnimator) {
                    ObjectAnimator anim = (ObjectAnimator) handler.mDelayedAnims.get(i);
                    if (anim.mAutoCancel && hasSameTargetAndProperties(anim)) {
                        anim.cancel();
                    }
                }
            }
        }
        if (DBG) {
            Log.d(LOG_TAG, "Anim target, duration: " + getTarget() + ", " + getDuration());
            for (int i = 0; i < mValues.length; ++i) {
                PropertyValuesHolder pvh = mValues[i];
                ArrayList<Keyframe> keyframes = pvh.mKeyframeSet.mKeyframes;
                Log.d(LOG_TAG, "   Values[" + i + "]: " +
                    pvh.getPropertyName() + ", " + keyframes.get(0).getValue() + ", " +
                    keyframes.get(pvh.mKeyframeSet.mNumKeyframes - 1).getValue());
            }
        }
        super.start();
    }

    /**
     * This function is called immediately before processing the first animation
     * frame of an animation. If there is a nonzero <code>startDelay</code>, the
     * function is called after that delay ends.
     * It takes care of the final initialization steps for the
     * animation. This includes setting mEvaluator, if the user has not yet
     * set it up, and the setter/getter methods, if the user did not supply
     * them.
     *
     *  <p>Overriders of this method should call the superclass method to cause
     *  internal mechanisms to be set up correctly.</p>
     */
    @Override
    void initAnimation() {
        if (!mInitialized) {
            // mValueType may change due to setter/getter setup; do this before calling super.init(),
            // which uses mValueType to set up the default type evaluator.
            final Object target = getTarget();
            if (target != null) {
                final int numValues = mValues.length;
                for (int i = 0; i < numValues; ++i) {
                    mValues[i].setupSetterAndGetter(target);
                }
            }
            super.initAnimation();
        }
    }

    /**
     * Sets the length of the animation. The default duration is 300 milliseconds.
     *
     * @param duration The length of the animation, in milliseconds.
     * @return ObjectAnimator The object called with setDuration(). This return
     * value makes it easier to compose statements together that construct and then set the
     * duration, as in
     * <code>ObjectAnimator.ofInt(target, propertyName, 0, 10).setDuration(500).start()</code>.
     */
    @Override
    @NonNull
    public ObjectAnimator setDuration(long duration) {
        super.setDuration(duration);
        return this;
    }


    /**
     * The target object whose property will be animated by this animation
     *
     * @return The object being animated
     */
    @Nullable
    public Object getTarget() {
        return mTarget == null ? null : mTarget.get();
    }

    /**
     * Sets the target object whose property will be animated by this animation
     *
     * @param target The object being animated
     */
    @Override
    public void setTarget(@Nullable Object target) {
        final Object oldTarget = getTarget();
        if (oldTarget != target) {
            mTarget = target == null ? null : new WeakReference<Object>(target);
            if (oldTarget != null && target != null && oldTarget.getClass() == target.getClass()) {
                return;
            }
            // New target type should cause re-initialization prior to starting
            mInitialized = false;
        }
    }

    @Override
    public void setupStartValues() {
        initAnimation();

        final Object target = getTarget();
        if (target != null) {
            final int numValues = mValues.length;
            for (int i = 0; i < numValues; ++i) {
                mValues[i].setupStartValue(target);
            }
        }
    }

    @Override
    public void setupEndValues() {
        initAnimation();

        final Object target = getTarget();
        if (target != null) {
            final int numValues = mValues.length;
            for (int i = 0; i < numValues; ++i) {
                mValues[i].setupEndValue(target);
            }
        }
    }

    /**
     * This method is called with the elapsed fraction of the animation during every
     * animation frame. This function turns the elapsed fraction into an interpolated fraction
     * and then into an animated value (from the evaluator. The function is called mostly during
     * animation updates, but it is also called when the <code>end()</code>
     * function is called, to set the final value on the property.
     *
     * <p>Overrides of this method must call the superclass to perform the calculation
     * of the animated value.</p>
     *
     * @param fraction The elapsed fraction of the animation.
     */
    @Override
    void animateValue(float fraction) {
        final Object target = getTarget();
        if (mTarget != null && target == null) {
            // We lost the target reference, cancel and clean up.
            cancel();
            return;
        }

        super.animateValue(fraction);
        int numValues = mValues.length;
        for (int i = 0; i < numValues; ++i) {
            mValues[i].setAnimatedValue(target);
        }
    }

    @Override
    public ObjectAnimator clone() {
        final ObjectAnimator anim = (ObjectAnimator) super.clone();
        return anim;
    }

    @Override
    @NonNull
    public String toString() {
        String returnVal = "ObjectAnimator@" + Integer.toHexString(hashCode()) + ", target " +
            getTarget();
        if (mValues != null) {
            for (int i = 0; i < mValues.length; ++i) {
                returnVal += "\n    " + mValues[i].toString();
            }
        }
        return returnVal;
    }
}
