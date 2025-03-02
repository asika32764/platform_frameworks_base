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

import android.graphics.Path;
import android.graphics.PointF;
import android.util.FloatMath;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.util.Log;
import android.util.Property;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class holds information about a property and the values that that property
 * should take on during an animation. PropertyValuesHolder objects can be used to create
 * animations with ValueAnimator or ObjectAnimator that operate on several different properties
 * in parallel.
 */
public class PropertyValuesHolder implements Cloneable {

    /**
     * The name of the property associated with the values. This need not be a real property,
     * unless this object is being used with ObjectAnimator. But this is the name by which
     * aniamted values are looked up with getAnimatedValue(String) in ValueAnimator.
     */
    String mPropertyName;

    /**
     * @hide
     */
    protected Property mProperty;

    /**
     * The setter function, if needed. ObjectAnimator hands off this functionality to
     * PropertyValuesHolder, since it holds all of the per-property information. This
     * property is automatically
     * derived when the animation starts in setupSetterAndGetter() if using ObjectAnimator.
     */
    Method mSetter = null;

    /**
     * The getter function, if needed. ObjectAnimator hands off this functionality to
     * PropertyValuesHolder, since it holds all of the per-property information. This
     * property is automatically
     * derived when the animation starts in setupSetterAndGetter() if using ObjectAnimator.
     * The getter is only derived and used if one of the values is null.
     */
    private Method mGetter = null;

    /**
     * The type of values supplied. This information is used both in deriving the setter/getter
     * functions and in deriving the type of TypeEvaluator.
     */
    Class mValueType;

    /**
     * The set of keyframes (time/value pairs) that define this animation.
     */
    KeyframeSet mKeyframeSet = null;


    // type evaluators for the primitive types handled by this implementation
    private static final TypeEvaluator sIntEvaluator = new IntEvaluator();
    private static final TypeEvaluator sFloatEvaluator = new FloatEvaluator();

    // We try several different types when searching for appropriate setter/getter functions.
    // The caller may have supplied values in a type that does not match the setter/getter
    // functions (such as the integers 0 and 1 to represent floating point values for alpha).
    // Also, the use of generics in constructors means that we end up with the Object versions
    // of primitive types (Float vs. float). But most likely, the setter/getter functions
    // will take primitive types instead.
    // So we supply an ordered array of other types to try before giving up.
    private static Class[] FLOAT_VARIANTS = {float.class, Float.class, double.class, int.class,
            Double.class, Integer.class};
    private static Class[] INTEGER_VARIANTS = {int.class, Integer.class, float.class, double.class,
            Float.class, Double.class};
    private static Class[] DOUBLE_VARIANTS = {double.class, Double.class, float.class, int.class,
            Float.class, Integer.class};

    // These maps hold all property entries for a particular class. This map
    // is used to speed up property/setter/getter lookups for a given class/property
    // combination. No need to use reflection on the combination more than once.
    private static final HashMap<Class, HashMap<String, Method>> sSetterPropertyMap =
            new HashMap<Class, HashMap<String, Method>>();
    private static final HashMap<Class, HashMap<String, Method>> sGetterPropertyMap =
            new HashMap<Class, HashMap<String, Method>>();

    // This lock is used to ensure that only one thread is accessing the property maps
    // at a time.
    final ReentrantReadWriteLock mPropertyMapLock = new ReentrantReadWriteLock();

    // Used to pass single value to varargs parameter in setter invocation
    final Object[] mTmpValueArray = new Object[1];

    /**
     * The type evaluator used to calculate the animated values. This evaluator is determined
     * automatically based on the type of the start/end objects passed into the constructor,
     * but the system only knows about the primitive types int and float. Any other
     * type will need to set the evaluator to a custom evaluator for that type.
     */
    private TypeEvaluator mEvaluator;

    /**
     * The value most recently calculated by calculateValue(). This is set during
     * that function and might be retrieved later either by ValueAnimator.animatedValue() or
     * by the property-setting logic in ObjectAnimator.animatedValue().
     */
    private Object mAnimatedValue;

    /**
     * Converts from the source Object type to the setter Object type.
     */
    private TypeConverter mConverter;

    /**
     * Internal utility constructor, used by the factory methods to set the property name.
     * @param propertyName The name of the property for this holder.
     */
    private PropertyValuesHolder(String propertyName) {
        mPropertyName = propertyName;
    }

    /**
     * Internal utility constructor, used by the factory methods to set the property.
     * @param property The property for this holder.
     */
    private PropertyValuesHolder(Property property) {
        mProperty = property;
        if (property != null) {
            mPropertyName = property.getName();
        }
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property name and
     * set of int values.
     * @param propertyName The name of the property being animated.
     * @param values The values that the named property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     */
    public static PropertyValuesHolder ofInt(String propertyName, int... values) {
        return new IntPropertyValuesHolder(propertyName, values);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property and
     * set of int values.
     * @param property The property being animated. Should not be null.
     * @param values The values that the property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     */
    public static PropertyValuesHolder ofInt(Property<?, Integer> property, int... values) {
        return new IntPropertyValuesHolder(property, values);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property name and
     * set of <code>int[]</code> values. At least two <code>int[]</code> values must be supplied,
     * a start and end value. If more values are supplied, the values will be animated from the
     * start, through all intermediate values to the end value. When used with ObjectAnimator,
     * the elements of the array represent the parameters of the setter function.
     *
     * @param propertyName The name of the property being animated. Can also be the
     *                     case-sensitive name of the entire setter method. Should not be null.
     * @param values The values that the property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     * @see IntArrayEvaluator#IntArrayEvaluator(int[])
     * @see ObjectAnimator#ofMultiInt(Object, String, TypeConverter, TypeEvaluator, Object[])
     */
    public static PropertyValuesHolder ofMultiInt(String propertyName, int[][] values) {
        if (values.length < 2) {
            throw new IllegalArgumentException("At least 2 values must be supplied");
        }
        int numParameters = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new IllegalArgumentException("values must not be null");
            }
            int length = values[i].length;
            if (i == 0) {
                numParameters = length;
            } else if (length != numParameters) {
                throw new IllegalArgumentException("Values must all have the same length");
            }
        }
        IntArrayEvaluator evaluator = new IntArrayEvaluator(new int[numParameters]);
        return new MultiIntValuesHolder(propertyName, null, evaluator, (Object[]) values);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property name to use
     * as a multi-int setter. The values are animated along the path, with the first
     * parameter of the setter set to the x coordinate and the second set to the y coordinate.
     *
     * @param propertyName The name of the property being animated. Can also be the
     *                     case-sensitive name of the entire setter method. Should not be null.
     *                     The setter must take exactly two <code>int</code> parameters.
     * @param path The Path along which the values should be animated.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     * @see ObjectAnimator#ofPropertyValuesHolder(Object, PropertyValuesHolder...)
     */
    public static PropertyValuesHolder ofMultiInt(String propertyName, Path path) {
        Keyframe[] keyframes = createKeyframes(path);
        KeyframeSet keyframeSet = KeyframeSet.ofKeyframe(keyframes);
        TypeEvaluator<PointF> evaluator = new PointFEvaluator(new PointF());
        PointFToIntArray converter = new PointFToIntArray();
        return new MultiIntValuesHolder(propertyName, converter, evaluator, keyframeSet);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property and
     * set of Object values for use with ObjectAnimator multi-value setters. The Object
     * values are converted to <code>int[]</code> using the converter.
     *
     * @param propertyName The property being animated or complete name of the setter.
     *                     Should not be null.
     * @param converter Used to convert the animated value to setter parameters.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values The values that the property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     * @see ObjectAnimator#ofMultiInt(Object, String, TypeConverter, TypeEvaluator, Object[])
     * @see ObjectAnimator#ofPropertyValuesHolder(Object, PropertyValuesHolder...)
     */
    public static <V> PropertyValuesHolder ofMultiInt(String propertyName,
            TypeConverter<V, int[]> converter, TypeEvaluator<V> evaluator, V... values) {
        return new MultiIntValuesHolder(propertyName, converter, evaluator, values);
    }

    /**
     * Constructs and returns a PropertyValuesHolder object with the specified property name or
     * setter name for use in a multi-int setter function using ObjectAnimator. The values can be
     * of any type, but the type should be consistent so that the supplied
     * {@link android.animation.TypeEvaluator} can be used to to evaluate the animated value. The
     * <code>converter</code> converts the values to parameters in the setter function.
     *
     * <p>At least two values must be supplied, a start and an end value.</p>
     *
     * @param propertyName The name of the property to associate with the set of values. This
     *                     may also be the complete name of a setter function.
     * @param converter    Converts <code>values</code> into int parameters for the setter.
     *                     Can be null if the Keyframes have int[] values.
     * @param evaluator    Used to interpolate between values.
     * @param values       The values at specific fractional times to evaluate between
     * @return A PropertyValuesHolder for a multi-int parameter setter.
     */
    public static <T> PropertyValuesHolder ofMultiInt(String propertyName,
            TypeConverter<T, int[]> converter, TypeEvaluator<T> evaluator, Keyframe... values) {
        KeyframeSet keyframeSet = KeyframeSet.ofKeyframe(values);
        return new MultiIntValuesHolder(propertyName, converter, evaluator, keyframeSet);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property name and
     * set of float values.
     * @param propertyName The name of the property being animated.
     * @param values The values that the named property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     */
    public static PropertyValuesHolder ofFloat(String propertyName, float... values) {
        return new FloatPropertyValuesHolder(propertyName, values);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property and
     * set of float values.
     * @param property The property being animated. Should not be null.
     * @param values The values that the property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     */
    public static PropertyValuesHolder ofFloat(Property<?, Float> property, float... values) {
        return new FloatPropertyValuesHolder(property, values);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property name and
     * set of <code>float[]</code> values. At least two <code>float[]</code> values must be supplied,
     * a start and end value. If more values are supplied, the values will be animated from the
     * start, through all intermediate values to the end value. When used with ObjectAnimator,
     * the elements of the array represent the parameters of the setter function.
     *
     * @param propertyName The name of the property being animated. Can also be the
     *                     case-sensitive name of the entire setter method. Should not be null.
     * @param values The values that the property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     * @see FloatArrayEvaluator#FloatArrayEvaluator(float[])
     * @see ObjectAnimator#ofMultiFloat(Object, String, TypeConverter, TypeEvaluator, Object[])
     */
    public static PropertyValuesHolder ofMultiFloat(String propertyName, float[][] values) {
        if (values.length < 2) {
            throw new IllegalArgumentException("At least 2 values must be supplied");
        }
        int numParameters = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new IllegalArgumentException("values must not be null");
            }
            int length = values[i].length;
            if (i == 0) {
                numParameters = length;
            } else if (length != numParameters) {
                throw new IllegalArgumentException("Values must all have the same length");
            }
        }
        FloatArrayEvaluator evaluator = new FloatArrayEvaluator(new float[numParameters]);
        return new MultiFloatValuesHolder(propertyName, null, evaluator, (Object[]) values);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property name to use
     * as a multi-float setter. The values are animated along the path, with the first
     * parameter of the setter set to the x coordinate and the second set to the y coordinate.
     *
     * @param propertyName The name of the property being animated. Can also be the
     *                     case-sensitive name of the entire setter method. Should not be null.
     *                     The setter must take exactly two <code>float</code> parameters.
     * @param path The Path along which the values should be animated.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     * @see ObjectAnimator#ofPropertyValuesHolder(Object, PropertyValuesHolder...)
     */
    public static PropertyValuesHolder ofMultiFloat(String propertyName, Path path) {
        Keyframe[] keyframes = createKeyframes(path);
        KeyframeSet keyframeSet = KeyframeSet.ofKeyframe(keyframes);
        TypeEvaluator<PointF> evaluator = new PointFEvaluator(new PointF());
        PointFToFloatArray converter = new PointFToFloatArray();
        return new MultiFloatValuesHolder(propertyName, converter, evaluator, keyframeSet);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property and
     * set of Object values for use with ObjectAnimator multi-value setters. The Object
     * values are converted to <code>float[]</code> using the converter.
     *
     * @param propertyName The property being animated or complete name of the setter.
     *                     Should not be null.
     * @param converter Used to convert the animated value to setter parameters.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values The values that the property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     * @see ObjectAnimator#ofMultiFloat(Object, String, TypeConverter, TypeEvaluator, Object[])
     */
    public static <V> PropertyValuesHolder ofMultiFloat(String propertyName,
            TypeConverter<V, float[]> converter, TypeEvaluator<V> evaluator, V... values) {
        return new MultiFloatValuesHolder(propertyName, converter, evaluator, values);
    }

    /**
     * Constructs and returns a PropertyValuesHolder object with the specified property name or
     * setter name for use in a multi-float setter function using ObjectAnimator. The values can be
     * of any type, but the type should be consistent so that the supplied
     * {@link android.animation.TypeEvaluator} can be used to to evaluate the animated value. The
     * <code>converter</code> converts the values to parameters in the setter function.
     *
     * <p>At least two values must be supplied, a start and an end value.</p>
     *
     * @param propertyName The name of the property to associate with the set of values. This
     *                     may also be the complete name of a setter function.
     * @param converter    Converts <code>values</code> into float parameters for the setter.
     *                     Can be null if the Keyframes have float[] values.
     * @param evaluator    Used to interpolate between values.
     * @param values       The values at specific fractional times to evaluate between
     * @return A PropertyValuesHolder for a multi-float parameter setter.
     */
    public static <T> PropertyValuesHolder ofMultiFloat(String propertyName,
            TypeConverter<T, float[]> converter, TypeEvaluator<T> evaluator, Keyframe... values) {
        KeyframeSet keyframeSet = KeyframeSet.ofKeyframe(values);
        return new MultiFloatValuesHolder(propertyName, converter, evaluator, keyframeSet);
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property name and
     * set of Object values. This variant also takes a TypeEvaluator because the system
     * cannot automatically interpolate between objects of unknown type.
     *
     * @param propertyName The name of the property being animated.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values The values that the named property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     */
    public static PropertyValuesHolder ofObject(String propertyName, TypeEvaluator evaluator,
            Object... values) {
        PropertyValuesHolder pvh = new PropertyValuesHolder(propertyName);
        pvh.setObjectValues(values);
        pvh.setEvaluator(evaluator);
        return pvh;
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property name and
     * a Path along which the values should be animated. This variant supports a
     * <code>TypeConverter</code> to convert from <code>PointF</code> to the target
     * type.
     *
     * @param propertyName The name of the property being animated.
     * @param converter Converts a PointF to the type associated with the setter. May be
     *                  null if conversion is unnecessary.
     * @param path The Path along which the values should be animated.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     */
    public static PropertyValuesHolder ofObject(String propertyName,
            TypeConverter<PointF, ?> converter, Path path) {
        Keyframe[] keyframes = createKeyframes(path);
        PropertyValuesHolder pvh = ofKeyframe(propertyName, keyframes);
        pvh.setEvaluator(new PointFEvaluator(new PointF()));
        pvh.setConverter(converter);
        return pvh;
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property and
     * set of Object values. This variant also takes a TypeEvaluator because the system
     * cannot automatically interpolate between objects of unknown type.
     *
     * @param property The property being animated. Should not be null.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values The values that the property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     */
    public static <V> PropertyValuesHolder ofObject(Property property,
            TypeEvaluator<V> evaluator, V... values) {
        PropertyValuesHolder pvh = new PropertyValuesHolder(property);
        pvh.setObjectValues(values);
        pvh.setEvaluator(evaluator);
        return pvh;
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property and
     * set of Object values. This variant also takes a TypeEvaluator because the system
     * cannot automatically interpolate between objects of unknown type. This variant also
     * takes a <code>TypeConverter</code> to convert from animated values to the type
     * of the property. If only one value is supplied, the <code>TypeConverter</code>
     * must be a {@link android.animation.BidirectionalTypeConverter} to retrieve the current
     * value.
     *
     * @param property The property being animated. Should not be null.
     * @param converter Converts the animated object to the Property type.
     * @param evaluator A TypeEvaluator that will be called on each animation frame to
     * provide the necessary interpolation between the Object values to derive the animated
     * value.
     * @param values The values that the property will animate between.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     * @see #setConverter(TypeConverter)
     * @see TypeConverter
     */
    public static <T, V> PropertyValuesHolder ofObject(Property<?, V> property,
            TypeConverter<T, V> converter, TypeEvaluator<T> evaluator, T... values) {
        PropertyValuesHolder pvh = new PropertyValuesHolder(property);
        pvh.setConverter(converter);
        pvh.setObjectValues(values);
        pvh.setEvaluator(evaluator);
        return pvh;
    }

    /**
     * Constructs and returns a PropertyValuesHolder with a given property and
     * a Path along which the values should be animated. This variant supports a
     * <code>TypeConverter</code> to convert from <code>PointF</code> to the target
     * type.
     *
     * @param property The property being animated. Should not be null.
     * @param converter Converts a PointF to the type associated with the setter. May be
     *                  null if conversion is unnecessary.
     * @param path The Path along which the values should be animated.
     * @return PropertyValuesHolder The constructed PropertyValuesHolder object.
     */
    public static <V> PropertyValuesHolder ofObject(Property<?, V> property,
            TypeConverter<PointF, V> converter, Path path) {
        Keyframe[] keyframes = createKeyframes(path);
        PropertyValuesHolder pvh = ofKeyframe(property, keyframes);
        pvh.setEvaluator(new PointFEvaluator(new PointF()));
        pvh.setConverter(converter);
        return pvh;
    }

    /**
     * Constructs and returns a PropertyValuesHolder object with the specified property name and set
     * of values. These values can be of any type, but the type should be consistent so that
     * an appropriate {@link android.animation.TypeEvaluator} can be found that matches
     * the common type.
     * <p>If there is only one value, it is assumed to be the end value of an animation,
     * and an initial value will be derived, if possible, by calling a getter function
     * on the object. Also, if any value is null, the value will be filled in when the animation
     * starts in the same way. This mechanism of automatically getting null values only works
     * if the PropertyValuesHolder object is used in conjunction
     * {@link ObjectAnimator}, and with a getter function
     * derived automatically from <code>propertyName</code>, since otherwise PropertyValuesHolder has
     * no way of determining what the value should be.
     * @param propertyName The name of the property associated with this set of values. This
     * can be the actual property name to be used when using a ObjectAnimator object, or
     * just a name used to get animated values, such as if this object is used with an
     * ValueAnimator object.
     * @param values The set of values to animate between.
     */
    public static PropertyValuesHolder ofKeyframe(String propertyName, Keyframe... values) {
        KeyframeSet keyframeSet = KeyframeSet.ofKeyframe(values);
        if (keyframeSet instanceof IntKeyframeSet) {
            return new IntPropertyValuesHolder(propertyName, (IntKeyframeSet) keyframeSet);
        } else if (keyframeSet instanceof FloatKeyframeSet) {
            return new FloatPropertyValuesHolder(propertyName, (FloatKeyframeSet) keyframeSet);
        }
        else {
            PropertyValuesHolder pvh = new PropertyValuesHolder(propertyName);
            pvh.mKeyframeSet = keyframeSet;
            pvh.mValueType = ((Keyframe)values[0]).getType();
            return pvh;
        }
    }

    /**
     * Constructs and returns a PropertyValuesHolder object with the specified property and set
     * of values. These values can be of any type, but the type should be consistent so that
     * an appropriate {@link android.animation.TypeEvaluator} can be found that matches
     * the common type.
     * <p>If there is only one value, it is assumed to be the end value of an animation,
     * and an initial value will be derived, if possible, by calling the property's
     * {@link android.util.Property#get(Object)} function.
     * Also, if any value is null, the value will be filled in when the animation
     * starts in the same way. This mechanism of automatically getting null values only works
     * if the PropertyValuesHolder object is used in conjunction with
     * {@link ObjectAnimator}, since otherwise PropertyValuesHolder has
     * no way of determining what the value should be.
     * @param property The property associated with this set of values. Should not be null.
     * @param values The set of values to animate between.
     */
    public static PropertyValuesHolder ofKeyframe(Property property, Keyframe... values) {
        KeyframeSet keyframeSet = KeyframeSet.ofKeyframe(values);
        if (keyframeSet instanceof IntKeyframeSet) {
            return new IntPropertyValuesHolder(property, (IntKeyframeSet) keyframeSet);
        } else if (keyframeSet instanceof FloatKeyframeSet) {
            return new FloatPropertyValuesHolder(property, (FloatKeyframeSet) keyframeSet);
        }
        else {
            PropertyValuesHolder pvh = new PropertyValuesHolder(property);
            pvh.mKeyframeSet = keyframeSet;
            pvh.mValueType = ((Keyframe)values[0]).getType();
            return pvh;
        }
    }

    /**
     * Set the animated values for this object to this set of ints.
     * If there is only one value, it is assumed to be the end value of an animation,
     * and an initial value will be derived, if possible, by calling a getter function
     * on the object. Also, if any value is null, the value will be filled in when the animation
     * starts in the same way. This mechanism of automatically getting null values only works
     * if the PropertyValuesHolder object is used in conjunction
     * {@link ObjectAnimator}, and with a getter function
     * derived automatically from <code>propertyName</code>, since otherwise PropertyValuesHolder has
     * no way of determining what the value should be.
     *
     * @param values One or more values that the animation will animate between.
     */
    public void setIntValues(int... values) {
        mValueType = int.class;
        mKeyframeSet = KeyframeSet.ofInt(values);
    }

    /**
     * Set the animated values for this object to this set of floats.
     * If there is only one value, it is assumed to be the end value of an animation,
     * and an initial value will be derived, if possible, by calling a getter function
     * on the object. Also, if any value is null, the value will be filled in when the animation
     * starts in the same way. This mechanism of automatically getting null values only works
     * if the PropertyValuesHolder object is used in conjunction
     * {@link ObjectAnimator}, and with a getter function
     * derived automatically from <code>propertyName</code>, since otherwise PropertyValuesHolder has
     * no way of determining what the value should be.
     *
     * @param values One or more values that the animation will animate between.
     */
    public void setFloatValues(float... values) {
        mValueType = float.class;
        mKeyframeSet = KeyframeSet.ofFloat(values);
    }

    /**
     * Set the animated values for this object to this set of Keyframes.
     *
     * @param values One or more values that the animation will animate between.
     */
    public void setKeyframes(Keyframe... values) {
        int numKeyframes = values.length;
        Keyframe keyframes[] = new Keyframe[Math.max(numKeyframes,2)];
        mValueType = ((Keyframe)values[0]).getType();
        for (int i = 0; i < numKeyframes; ++i) {
            keyframes[i] = (Keyframe)values[i];
        }
        mKeyframeSet = new KeyframeSet(keyframes);
    }

    /**
     * Set the animated values for this object to this set of Objects.
     * If there is only one value, it is assumed to be the end value of an animation,
     * and an initial value will be derived, if possible, by calling a getter function
     * on the object. Also, if any value is null, the value will be filled in when the animation
     * starts in the same way. This mechanism of automatically getting null values only works
     * if the PropertyValuesHolder object is used in conjunction
     * {@link ObjectAnimator}, and with a getter function
     * derived automatically from <code>propertyName</code>, since otherwise PropertyValuesHolder has
     * no way of determining what the value should be.
     * 
     * @param values One or more values that the animation will animate between.
     */
    public void setObjectValues(Object... values) {
        mValueType = values[0].getClass();
        mKeyframeSet = KeyframeSet.ofObject(values);
    }

    /**
     * Sets the converter to convert from the values type to the setter's parameter type.
     * If only one value is supplied, <var>converter</var> must be a
     * {@link android.animation.BidirectionalTypeConverter}.
     * @param converter The converter to use to convert values.
     */
    public void setConverter(TypeConverter converter) {
        mConverter = converter;
    }

    /**
     * Determine the setter or getter function using the JavaBeans convention of setFoo or
     * getFoo for a property named 'foo'. This function figures out what the name of the
     * function should be and uses reflection to find the Method with that name on the
     * target object.
     *
     * @param targetClass The class to search for the method
     * @param prefix "set" or "get", depending on whether we need a setter or getter.
     * @param valueType The type of the parameter (in the case of a setter). This type
     * is derived from the values set on this PropertyValuesHolder. This type is used as
     * a first guess at the parameter type, but we check for methods with several different
     * types to avoid problems with slight mis-matches between supplied values and actual
     * value types used on the setter.
     * @return Method the method associated with mPropertyName.
     */
    private Method getPropertyFunction(Class targetClass, String prefix, Class valueType) {
        // TODO: faster implementation...
        Method returnVal = null;
        String methodName = getMethodName(prefix, mPropertyName);
        Class args[] = null;
        if (valueType == null) {
            try {
                returnVal = targetClass.getMethod(methodName, args);
            } catch (NoSuchMethodException e) {
                // Swallow the error, log it later
            }
        } else {
            args = new Class[1];
            Class typeVariants[];
            if (valueType.equals(Float.class)) {
                typeVariants = FLOAT_VARIANTS;
            } else if (valueType.equals(Integer.class)) {
                typeVariants = INTEGER_VARIANTS;
            } else if (valueType.equals(Double.class)) {
                typeVariants = DOUBLE_VARIANTS;
            } else {
                typeVariants = new Class[1];
                typeVariants[0] = valueType;
            }
            for (Class typeVariant : typeVariants) {
                args[0] = typeVariant;
                try {
                    returnVal = targetClass.getMethod(methodName, args);
                    if (mConverter == null) {
                        // change the value type to suit
                        mValueType = typeVariant;
                    }
                    return returnVal;
                } catch (NoSuchMethodException e) {
                    // Swallow the error and keep trying other variants
                }
            }
            // If we got here, then no appropriate function was found
        }

        if (returnVal == null) {
            Log.w("PropertyValuesHolder", "Method " +
                    getMethodName(prefix, mPropertyName) + "() with type " + valueType +
                    " not found on target class " + targetClass);
        }

        return returnVal;
    }


    /**
     * Returns the setter or getter requested. This utility function checks whether the
     * requested method exists in the propertyMapMap cache. If not, it calls another
     * utility function to request the Method from the targetClass directly.
     * @param targetClass The Class on which the requested method should exist.
     * @param propertyMapMap The cache of setters/getters derived so far.
     * @param prefix "set" or "get", for the setter or getter.
     * @param valueType The type of parameter passed into the method (null for getter).
     * @return Method the method associated with mPropertyName.
     */
    private Method setupSetterOrGetter(Class targetClass,
            HashMap<Class, HashMap<String, Method>> propertyMapMap,
            String prefix, Class valueType) {
        Method setterOrGetter = null;
        try {
            // Have to lock property map prior to reading it, to guard against
            // another thread putting something in there after we've checked it
            // but before we've added an entry to it
            mPropertyMapLock.writeLock().lock();
            HashMap<String, Method> propertyMap = propertyMapMap.get(targetClass);
            if (propertyMap != null) {
                setterOrGetter = propertyMap.get(mPropertyName);
            }
            if (setterOrGetter == null) {
                setterOrGetter = getPropertyFunction(targetClass, prefix, valueType);
                if (propertyMap == null) {
                    propertyMap = new HashMap<String, Method>();
                    propertyMapMap.put(targetClass, propertyMap);
                }
                propertyMap.put(mPropertyName, setterOrGetter);
            }
        } finally {
            mPropertyMapLock.writeLock().unlock();
        }
        return setterOrGetter;
    }

    /**
     * Utility function to get the setter from targetClass
     * @param targetClass The Class on which the requested method should exist.
     */
    void setupSetter(Class targetClass) {
        Class<?> propertyType = mConverter == null ? mValueType : mConverter.getTargetType();
        mSetter = setupSetterOrGetter(targetClass, sSetterPropertyMap, "set", propertyType);
    }

    /**
     * Utility function to get the getter from targetClass
     */
    private void setupGetter(Class targetClass) {
        mGetter = setupSetterOrGetter(targetClass, sGetterPropertyMap, "get", null);
    }

    /**
     * Internal function (called from ObjectAnimator) to set up the setter and getter
     * prior to running the animation. If the setter has not been manually set for this
     * object, it will be derived automatically given the property name, target object, and
     * types of values supplied. If no getter has been set, it will be supplied iff any of the
     * supplied values was null. If there is a null value, then the getter (supplied or derived)
     * will be called to set those null values to the current value of the property
     * on the target object.
     * @param target The object on which the setter (and possibly getter) exist.
     */
    void setupSetterAndGetter(Object target) {
        if (mProperty != null) {
            // check to make sure that mProperty is on the class of target
            try {
                Object testValue = null;
                for (Keyframe kf : mKeyframeSet.mKeyframes) {
                    if (!kf.hasValue()) {
                        if (testValue == null) {
                            testValue = convertBack(mProperty.get(target));
                        }
                        kf.setValue(testValue);
                    }
                }
                return;
            } catch (ClassCastException e) {
                Log.w("PropertyValuesHolder","No such property (" + mProperty.getName() +
                        ") on target object " + target + ". Trying reflection instead");
                mProperty = null;
            }
        }
        Class targetClass = target.getClass();
        if (mSetter == null) {
            setupSetter(targetClass);
        }
        for (Keyframe kf : mKeyframeSet.mKeyframes) {
            if (!kf.hasValue()) {
                if (mGetter == null) {
                    setupGetter(targetClass);
                    if (mGetter == null) {
                        // Already logged the error - just return to avoid NPE
                        return;
                    }
                }
                try {
                    Object value = convertBack(mGetter.invoke(target));
                    kf.setValue(value);
                } catch (InvocationTargetException e) {
                    Log.e("PropertyValuesHolder", e.toString());
                } catch (IllegalAccessException e) {
                    Log.e("PropertyValuesHolder", e.toString());
                }
            }
        }
    }

    private Object convertBack(Object value) {
        if (mConverter != null) {
            if (!(mConverter instanceof BidirectionalTypeConverter)) {
                throw new IllegalArgumentException("Converter "
                        + mConverter.getClass().getName()
                        + " must be a BidirectionalTypeConverter");
            }
            value = ((BidirectionalTypeConverter) mConverter).convertBack(value);
        }
        return value;
    }

    /**
     * Utility function to set the value stored in a particular Keyframe. The value used is
     * whatever the value is for the property name specified in the keyframe on the target object.
     *
     * @param target The target object from which the current value should be extracted.
     * @param kf The keyframe which holds the property name and value.
     */
    private void setupValue(Object target, Keyframe kf) {
        if (mProperty != null) {
            Object value = convertBack(mProperty.get(target));
            kf.setValue(value);
        }
        try {
            if (mGetter == null) {
                Class targetClass = target.getClass();
                setupGetter(targetClass);
                if (mGetter == null) {
                    // Already logged the error - just return to avoid NPE
                    return;
                }
            }
            Object value = convertBack(mGetter.invoke(target));
            kf.setValue(value);
        } catch (InvocationTargetException e) {
            Log.e("PropertyValuesHolder", e.toString());
        } catch (IllegalAccessException e) {
            Log.e("PropertyValuesHolder", e.toString());
        }
    }

    /**
     * This function is called by ObjectAnimator when setting the start values for an animation.
     * The start values are set according to the current values in the target object. The
     * property whose value is extracted is whatever is specified by the propertyName of this
     * PropertyValuesHolder object.
     *
     * @param target The object which holds the start values that should be set.
     */
    void setupStartValue(Object target) {
        setupValue(target, mKeyframeSet.mKeyframes.get(0));
    }

    /**
     * This function is called by ObjectAnimator when setting the end values for an animation.
     * The end values are set according to the current values in the target object. The
     * property whose value is extracted is whatever is specified by the propertyName of this
     * PropertyValuesHolder object.
     *
     * @param target The object which holds the start values that should be set.
     */
    void setupEndValue(Object target) {
        setupValue(target, mKeyframeSet.mKeyframes.get(mKeyframeSet.mKeyframes.size() - 1));
    }

    @Override
    public PropertyValuesHolder clone() {
        try {
            PropertyValuesHolder newPVH = (PropertyValuesHolder) super.clone();
            newPVH.mPropertyName = mPropertyName;
            newPVH.mProperty = mProperty;
            newPVH.mKeyframeSet = mKeyframeSet.clone();
            newPVH.mEvaluator = mEvaluator;
            return newPVH;
        } catch (CloneNotSupportedException e) {
            // won't reach here
            return null;
        }
    }

    /**
     * Internal function to set the value on the target object, using the setter set up
     * earlier on this PropertyValuesHolder object. This function is called by ObjectAnimator
     * to handle turning the value calculated by ValueAnimator into a value set on the object
     * according to the name of the property.
     * @param target The target object on which the value is set
     */
    void setAnimatedValue(Object target) {
        if (mProperty != null) {
            mProperty.set(target, getAnimatedValue());
        }
        if (mSetter != null) {
            try {
                mTmpValueArray[0] = getAnimatedValue();
                mSetter.invoke(target, mTmpValueArray);
            } catch (InvocationTargetException e) {
                Log.e("PropertyValuesHolder", e.toString());
            } catch (IllegalAccessException e) {
                Log.e("PropertyValuesHolder", e.toString());
            }
        }
    }

    /**
     * Internal function, called by ValueAnimator, to set up the TypeEvaluator that will be used
     * to calculate animated values.
     */
    void init() {
        if (mEvaluator == null) {
            // We already handle int and float automatically, but not their Object
            // equivalents
            mEvaluator = (mValueType == Integer.class) ? sIntEvaluator :
                    (mValueType == Float.class) ? sFloatEvaluator :
                    null;
        }
        if (mEvaluator != null) {
            // KeyframeSet knows how to evaluate the common types - only give it a custom
            // evaluator if one has been set on this class
            mKeyframeSet.setEvaluator(mEvaluator);
        }
    }

    /**
     * The TypeEvaluator will be automatically determined based on the type of values
     * supplied to PropertyValuesHolder. The evaluator can be manually set, however, if so
     * desired. This may be important in cases where either the type of the values supplied
     * do not match the way that they should be interpolated between, or if the values
     * are of a custom type or one not currently understood by the animation system. Currently,
     * only values of type float and int (and their Object equivalents: Float
     * and Integer) are  correctly interpolated; all other types require setting a TypeEvaluator.
     * @param evaluator
     */
    public void setEvaluator(TypeEvaluator evaluator) {
        mEvaluator = evaluator;
        mKeyframeSet.setEvaluator(evaluator);
    }

    /**
     * Function used to calculate the value according to the evaluator set up for
     * this PropertyValuesHolder object. This function is called by ValueAnimator.animateValue().
     *
     * @param fraction The elapsed, interpolated fraction of the animation.
     */
    void calculateValue(float fraction) {
        Object value = mKeyframeSet.getValue(fraction);
        mAnimatedValue = mConverter == null ? value : mConverter.convert(value);
    }

    /**
     * Sets the name of the property that will be animated. This name is used to derive
     * a setter function that will be called to set animated values.
     * For example, a property name of <code>foo</code> will result
     * in a call to the function <code>setFoo()</code> on the target object. If either
     * <code>valueFrom</code> or <code>valueTo</code> is null, then a getter function will
     * also be derived and called.
     *
     * <p>Note that the setter function derived from this property name
     * must take the same parameter type as the
     * <code>valueFrom</code> and <code>valueTo</code> properties, otherwise the call to
     * the setter function will fail.</p>
     *
     * @param propertyName The name of the property being animated.
     */
    public void setPropertyName(String propertyName) {
        mPropertyName = propertyName;
    }

    /**
     * Sets the property that will be animated.
     *
     * <p>Note that if this PropertyValuesHolder object is used with ObjectAnimator, the property
     * must exist on the target object specified in that ObjectAnimator.</p>
     *
     * @param property The property being animated.
     */
    public void setProperty(Property property) {
        mProperty = property;
    }

    /**
     * Gets the name of the property that will be animated. This name will be used to derive
     * a setter function that will be called to set animated values.
     * For example, a property name of <code>foo</code> will result
     * in a call to the function <code>setFoo()</code> on the target object. If either
     * <code>valueFrom</code> or <code>valueTo</code> is null, then a getter function will
     * also be derived and called.
     */
    public String getPropertyName() {
        return mPropertyName;
    }

    /**
     * Internal function, called by ValueAnimator and ObjectAnimator, to retrieve the value
     * most recently calculated in calculateValue().
     * @return
     */
    Object getAnimatedValue() {
        return mAnimatedValue;
    }

    @Override
    public String toString() {
        return mPropertyName + ": " + mKeyframeSet.toString();
    }

    /**
     * Utility method to derive a setter/getter method name from a property name, where the
     * prefix is typically "set" or "get" and the first letter of the property name is
     * capitalized.
     *
     * @param prefix The precursor to the method name, before the property name begins, typically
     * "set" or "get".
     * @param propertyName The name of the property that represents the bulk of the method name
     * after the prefix. The first letter of this word will be capitalized in the resulting
     * method name.
     * @return String the property name converted to a method name according to the conventions
     * specified above.
     */
    static String getMethodName(String prefix, String propertyName) {
        if (propertyName == null || propertyName.length() == 0) {
            // shouldn't get here
            return prefix;
        }
        char firstLetter = Character.toUpperCase(propertyName.charAt(0));
        String theRest = propertyName.substring(1);
        return prefix + firstLetter + theRest;
    }

    static class IntPropertyValuesHolder extends PropertyValuesHolder {

        // Cache JNI functions to avoid looking them up twice
        private static final HashMap<Class, HashMap<String, Long>> sJNISetterPropertyMap =
                new HashMap<Class, HashMap<String, Long>>();
        long mJniSetter;
        private IntProperty mIntProperty;

        IntKeyframeSet mIntKeyframeSet;
        int mIntAnimatedValue;

        public IntPropertyValuesHolder(String propertyName, IntKeyframeSet keyframeSet) {
            super(propertyName);
            mValueType = int.class;
            mKeyframeSet = keyframeSet;
            mIntKeyframeSet = (IntKeyframeSet) mKeyframeSet;
        }

        public IntPropertyValuesHolder(Property property, IntKeyframeSet keyframeSet) {
            super(property);
            mValueType = int.class;
            mKeyframeSet = keyframeSet;
            mIntKeyframeSet = (IntKeyframeSet) mKeyframeSet;
            if (property instanceof  IntProperty) {
                mIntProperty = (IntProperty) mProperty;
            }
        }

        public IntPropertyValuesHolder(String propertyName, int... values) {
            super(propertyName);
            setIntValues(values);
        }

        public IntPropertyValuesHolder(Property property, int... values) {
            super(property);
            setIntValues(values);
            if (property instanceof  IntProperty) {
                mIntProperty = (IntProperty) mProperty;
            }
        }

        @Override
        public void setIntValues(int... values) {
            super.setIntValues(values);
            mIntKeyframeSet = (IntKeyframeSet) mKeyframeSet;
        }

        @Override
        void calculateValue(float fraction) {
            mIntAnimatedValue = mIntKeyframeSet.getIntValue(fraction);
        }

        @Override
        Object getAnimatedValue() {
            return mIntAnimatedValue;
        }

        @Override
        public IntPropertyValuesHolder clone() {
            IntPropertyValuesHolder newPVH = (IntPropertyValuesHolder) super.clone();
            newPVH.mIntKeyframeSet = (IntKeyframeSet) newPVH.mKeyframeSet;
            return newPVH;
        }

        /**
         * Internal function to set the value on the target object, using the setter set up
         * earlier on this PropertyValuesHolder object. This function is called by ObjectAnimator
         * to handle turning the value calculated by ValueAnimator into a value set on the object
         * according to the name of the property.
         * @param target The target object on which the value is set
         */
        @Override
        void setAnimatedValue(Object target) {
            if (mIntProperty != null) {
                mIntProperty.setValue(target, mIntAnimatedValue);
                return;
            }
            if (mProperty != null) {
                mProperty.set(target, mIntAnimatedValue);
                return;
            }
            if (mJniSetter != 0) {
                nCallIntMethod(target, mJniSetter, mIntAnimatedValue);
                return;
            }
            if (mSetter != null) {
                try {
                    mTmpValueArray[0] = mIntAnimatedValue;
                    mSetter.invoke(target, mTmpValueArray);
                } catch (InvocationTargetException e) {
                    Log.e("PropertyValuesHolder", e.toString());
                } catch (IllegalAccessException e) {
                    Log.e("PropertyValuesHolder", e.toString());
                }
            }
        }

        @Override
        void setupSetter(Class targetClass) {
            if (mProperty != null) {
                return;
            }
            // Check new static hashmap<propName, int> for setter method
            try {
                mPropertyMapLock.writeLock().lock();
                HashMap<String, Long> propertyMap = sJNISetterPropertyMap.get(targetClass);
                if (propertyMap != null) {
                    Long jniSetter = propertyMap.get(mPropertyName);
                    if (jniSetter != null) {
                        mJniSetter = jniSetter;
                    }
                }
                if (mJniSetter == 0) {
                    String methodName = getMethodName("set", mPropertyName);
                    mJniSetter = nGetIntMethod(targetClass, methodName);
                    if (mJniSetter != 0) {
                        if (propertyMap == null) {
                            propertyMap = new HashMap<String, Long>();
                            sJNISetterPropertyMap.put(targetClass, propertyMap);
                        }
                        propertyMap.put(mPropertyName, mJniSetter);
                    }
                }
            } catch (NoSuchMethodError e) {
                // Couldn't find it via JNI - try reflection next. Probably means the method
                // doesn't exist, or the type is wrong. An error will be logged later if
                // reflection fails as well.
            } finally {
                mPropertyMapLock.writeLock().unlock();
            }
            if (mJniSetter == 0) {
                // Couldn't find method through fast JNI approach - just use reflection
                super.setupSetter(targetClass);
            }
        }
    }

    static class FloatPropertyValuesHolder extends PropertyValuesHolder {

        // Cache JNI functions to avoid looking them up twice
        private static final HashMap<Class, HashMap<String, Long>> sJNISetterPropertyMap =
                new HashMap<Class, HashMap<String, Long>>();
        long mJniSetter;
        private FloatProperty mFloatProperty;

        FloatKeyframeSet mFloatKeyframeSet;
        float mFloatAnimatedValue;

        public FloatPropertyValuesHolder(String propertyName, FloatKeyframeSet keyframeSet) {
            super(propertyName);
            mValueType = float.class;
            mKeyframeSet = keyframeSet;
            mFloatKeyframeSet = (FloatKeyframeSet) mKeyframeSet;
        }

        public FloatPropertyValuesHolder(Property property, FloatKeyframeSet keyframeSet) {
            super(property);
            mValueType = float.class;
            mKeyframeSet = keyframeSet;
            mFloatKeyframeSet = (FloatKeyframeSet) mKeyframeSet;
            if (property instanceof FloatProperty) {
                mFloatProperty = (FloatProperty) mProperty;
            }
        }

        public FloatPropertyValuesHolder(String propertyName, float... values) {
            super(propertyName);
            setFloatValues(values);
        }

        public FloatPropertyValuesHolder(Property property, float... values) {
            super(property);
            setFloatValues(values);
            if (property instanceof  FloatProperty) {
                mFloatProperty = (FloatProperty) mProperty;
            }
        }

        @Override
        public void setFloatValues(float... values) {
            super.setFloatValues(values);
            mFloatKeyframeSet = (FloatKeyframeSet) mKeyframeSet;
        }

        @Override
        void calculateValue(float fraction) {
            mFloatAnimatedValue = mFloatKeyframeSet.getFloatValue(fraction);
        }

        @Override
        Object getAnimatedValue() {
            return mFloatAnimatedValue;
        }

        @Override
        public FloatPropertyValuesHolder clone() {
            FloatPropertyValuesHolder newPVH = (FloatPropertyValuesHolder) super.clone();
            newPVH.mFloatKeyframeSet = (FloatKeyframeSet) newPVH.mKeyframeSet;
            return newPVH;
        }

        /**
         * Internal function to set the value on the target object, using the setter set up
         * earlier on this PropertyValuesHolder object. This function is called by ObjectAnimator
         * to handle turning the value calculated by ValueAnimator into a value set on the object
         * according to the name of the property.
         * @param target The target object on which the value is set
         */
        @Override
        void setAnimatedValue(Object target) {
            if (mFloatProperty != null) {
                mFloatProperty.setValue(target, mFloatAnimatedValue);
                return;
            }
            if (mProperty != null) {
                mProperty.set(target, mFloatAnimatedValue);
                return;
            }
            if (mJniSetter != 0) {
                nCallFloatMethod(target, mJniSetter, mFloatAnimatedValue);
                return;
            }
            if (mSetter != null) {
                try {
                    mTmpValueArray[0] = mFloatAnimatedValue;
                    mSetter.invoke(target, mTmpValueArray);
                } catch (InvocationTargetException e) {
                    Log.e("PropertyValuesHolder", e.toString());
                } catch (IllegalAccessException e) {
                    Log.e("PropertyValuesHolder", e.toString());
                }
            }
        }

        @Override
        void setupSetter(Class targetClass) {
            if (mProperty != null) {
                return;
            }
            // Check new static hashmap<propName, int> for setter method
            try {
                mPropertyMapLock.writeLock().lock();
                HashMap<String, Long> propertyMap = sJNISetterPropertyMap.get(targetClass);
                if (propertyMap != null) {
                    Long jniSetter = propertyMap.get(mPropertyName);
                    if (jniSetter != null) {
                        mJniSetter = jniSetter;
                    }
                }
                if (mJniSetter == 0) {
                    String methodName = getMethodName("set", mPropertyName);
                    mJniSetter = nGetFloatMethod(targetClass, methodName);
                    if (mJniSetter != 0) {
                        if (propertyMap == null) {
                            propertyMap = new HashMap<String, Long>();
                            sJNISetterPropertyMap.put(targetClass, propertyMap);
                        }
                        propertyMap.put(mPropertyName, mJniSetter);
                    }
                }
            } catch (NoSuchMethodError e) {
                // Couldn't find it via JNI - try reflection next. Probably means the method
                // doesn't exist, or the type is wrong. An error will be logged later if
                // reflection fails as well.
            } finally {
                mPropertyMapLock.writeLock().unlock();
            }
            if (mJniSetter == 0) {
                // Couldn't find method through fast JNI approach - just use reflection
                super.setupSetter(targetClass);
            }
        }

    }

    static class MultiFloatValuesHolder extends PropertyValuesHolder {
        private long mJniSetter;
        private static final HashMap<Class, HashMap<String, Long>> sJNISetterPropertyMap =
                new HashMap<Class, HashMap<String, Long>>();

        public MultiFloatValuesHolder(String propertyName, TypeConverter converter,
                TypeEvaluator evaluator, Object... values) {
            super(propertyName);
            setConverter(converter);
            setObjectValues(values);
            setEvaluator(evaluator);
        }

        public MultiFloatValuesHolder(String propertyName, TypeConverter converter,
                TypeEvaluator evaluator, KeyframeSet keyframeSet) {
            super(propertyName);
            setConverter(converter);
            mKeyframeSet = keyframeSet;
            setEvaluator(evaluator);
        }

        /**
         * Internal function to set the value on the target object, using the setter set up
         * earlier on this PropertyValuesHolder object. This function is called by ObjectAnimator
         * to handle turning the value calculated by ValueAnimator into a value set on the object
         * according to the name of the property.
         *
         * @param target The target object on which the value is set
         */
        @Override
        void setAnimatedValue(Object target) {
            float[] values = (float[]) getAnimatedValue();
            int numParameters = values.length;
            if (mJniSetter != 0) {
                switch (numParameters) {
                    case 1:
                        nCallFloatMethod(target, mJniSetter, values[0]);
                        break;
                    case 2:
                        nCallTwoFloatMethod(target, mJniSetter, values[0], values[1]);
                        break;
                    case 4:
                        nCallFourFloatMethod(target, mJniSetter, values[0], values[1],
                                values[2], values[3]);
                        break;
                    default: {
                        nCallMultipleFloatMethod(target, mJniSetter, values);
                        break;
                    }
                }
            }
        }

        /**
         * Internal function (called from ObjectAnimator) to set up the setter and getter
         * prior to running the animation. No getter can be used for multiple parameters.
         *
         * @param target The object on which the setter exists.
         */
        @Override
        void setupSetterAndGetter(Object target) {
            setupSetter(target.getClass());
        }

        @Override
        void setupSetter(Class targetClass) {
            if (mJniSetter != 0) {
                return;
            }
            try {
                mPropertyMapLock.writeLock().lock();
                HashMap<String, Long> propertyMap = sJNISetterPropertyMap.get(targetClass);
                if (propertyMap != null) {
                    Long jniSetterLong = propertyMap.get(mPropertyName);
                    if (jniSetterLong != null) {
                        mJniSetter = jniSetterLong;
                    }
                }
                if (mJniSetter == 0) {
                    String methodName = getMethodName("set", mPropertyName);
                    calculateValue(0f);
                    float[] values = (float[]) getAnimatedValue();
                    int numParams = values.length;
                    try {
                        mJniSetter = nGetMultipleFloatMethod(targetClass, methodName, numParams);
                    } catch (NoSuchMethodError e) {
                        // try without the 'set' prefix
                        mJniSetter = nGetMultipleFloatMethod(targetClass, mPropertyName, numParams);
                    }
                    if (mJniSetter != 0) {
                        if (propertyMap == null) {
                            propertyMap = new HashMap<String, Long>();
                            sJNISetterPropertyMap.put(targetClass, propertyMap);
                        }
                        propertyMap.put(mPropertyName, mJniSetter);
                    }
                }
            } finally {
                mPropertyMapLock.writeLock().unlock();
            }
        }
    }

    static class MultiIntValuesHolder extends PropertyValuesHolder {
        private long mJniSetter;
        private static final HashMap<Class, HashMap<String, Long>> sJNISetterPropertyMap =
                new HashMap<Class, HashMap<String, Long>>();

        public MultiIntValuesHolder(String propertyName, TypeConverter converter,
                TypeEvaluator evaluator, Object... values) {
            super(propertyName);
            setConverter(converter);
            setObjectValues(values);
            setEvaluator(evaluator);
        }

        public MultiIntValuesHolder(String propertyName, TypeConverter converter,
                TypeEvaluator evaluator, KeyframeSet keyframeSet) {
            super(propertyName);
            setConverter(converter);
            mKeyframeSet = keyframeSet;
            setEvaluator(evaluator);
        }

        /**
         * Internal function to set the value on the target object, using the setter set up
         * earlier on this PropertyValuesHolder object. This function is called by ObjectAnimator
         * to handle turning the value calculated by ValueAnimator into a value set on the object
         * according to the name of the property.
         *
         * @param target The target object on which the value is set
         */
        @Override
        void setAnimatedValue(Object target) {
            int[] values = (int[]) getAnimatedValue();
            int numParameters = values.length;
            if (mJniSetter != 0) {
                switch (numParameters) {
                    case 1:
                        nCallIntMethod(target, mJniSetter, values[0]);
                        break;
                    case 2:
                        nCallTwoIntMethod(target, mJniSetter, values[0], values[1]);
                        break;
                    case 4:
                        nCallFourIntMethod(target, mJniSetter, values[0], values[1],
                                values[2], values[3]);
                        break;
                    default: {
                        nCallMultipleIntMethod(target, mJniSetter, values);
                        break;
                    }
                }
            }
        }

        /**
         * Internal function (called from ObjectAnimator) to set up the setter and getter
         * prior to running the animation. No getter can be used for multiple parameters.
         *
         * @param target The object on which the setter exists.
         */
        @Override
        void setupSetterAndGetter(Object target) {
            setupSetter(target.getClass());
        }

        @Override
        void setupSetter(Class targetClass) {
            if (mJniSetter != 0) {
                return;
            }
            try {
                mPropertyMapLock.writeLock().lock();
                HashMap<String, Long> propertyMap = sJNISetterPropertyMap.get(targetClass);
                if (propertyMap != null) {
                    Long jniSetterLong = propertyMap.get(mPropertyName);
                    if (jniSetterLong != null) {
                        mJniSetter = jniSetterLong;
                    }
                }
                if (mJniSetter == 0) {
                    String methodName = getMethodName("set", mPropertyName);
                    calculateValue(0f);
                    int[] values = (int[]) getAnimatedValue();
                    int numParams = values.length;
                    try {
                        mJniSetter = nGetMultipleIntMethod(targetClass, methodName, numParams);
                    } catch (NoSuchMethodError e) {
                        // try without the 'set' prefix
                        mJniSetter = nGetMultipleIntMethod(targetClass, mPropertyName, numParams);
                    }
                    if (mJniSetter != 0) {
                        if (propertyMap == null) {
                            propertyMap = new HashMap<String, Long>();
                            sJNISetterPropertyMap.put(targetClass, propertyMap);
                        }
                        propertyMap.put(mPropertyName, mJniSetter);
                    }
                }
            } finally {
                mPropertyMapLock.writeLock().unlock();
            }
        }
    }

    /* Path interpolation relies on approximating the Path as a series of line segments.
       The line segments are recursively divided until there is less than 1/2 pixel error
       between the lines and the curve. Each point of the line segment is converted
       to a Keyframe and a linear interpolation between Keyframes creates a good approximation
       of the curve.

       The fraction for each Keyframe is the length along the Path to the point, divided by
       the total Path length. Two points may have the same fraction in the case of a move
       command causing a disjoint Path.

       The value for each Keyframe is either the point as a PointF or one of the x or y
       coordinates as an int or float. In the latter case, two Keyframes are generated for
       each point that have the same fraction. */

    /**
     * Returns separate Keyframes arrays for the x and y coordinates along a Path. If
     * isInt is true, the Keyframes will be IntKeyframes, otherwise they will be FloatKeyframes.
     * The element at index 0 are the x coordinate Keyframes and element at index 1 are the
     * y coordinate Keyframes. The returned values can be linearly interpolated and get less
     * than 1/2 pixel error.
     */
    static Keyframe[][] createKeyframes(Path path, boolean isInt) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("The path must not be null or empty");
        }
        float[] pointComponents = path.approximate(0.5f);

        int numPoints = pointComponents.length / 3;

        Keyframe[][] keyframes = new Keyframe[2][];
        keyframes[0] = new Keyframe[numPoints];
        keyframes[1] = new Keyframe[numPoints];
        int componentIndex = 0;
        for (int i = 0; i < numPoints; i++) {
            float fraction = pointComponents[componentIndex++];
            float x = pointComponents[componentIndex++];
            float y = pointComponents[componentIndex++];
            if (isInt) {
                keyframes[0][i] = Keyframe.ofInt(fraction, Math.round(x));
                keyframes[1][i] = Keyframe.ofInt(fraction, Math.round(y));
            } else {
                keyframes[0][i] = Keyframe.ofFloat(fraction, x);
                keyframes[1][i] = Keyframe.ofFloat(fraction, y);
            }
        }
        return keyframes;
    }

    /**
     * Returns PointF Keyframes for a Path. The resulting points can be linearly interpolated
     * with less than 1/2 pixel in error.
     */
    private static Keyframe[] createKeyframes(Path path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("The path must not be null or empty");
        }
        float[] pointComponents = path.approximate(0.5f);

        int numPoints = pointComponents.length / 3;

        Keyframe[] keyframes = new Keyframe[numPoints];
        int componentIndex = 0;
        for (int i = 0; i < numPoints; i++) {
            float fraction = pointComponents[componentIndex++];
            float x = pointComponents[componentIndex++];
            float y = pointComponents[componentIndex++];
            keyframes[i] = Keyframe.ofObject(fraction, new PointF(x, y));
        }
        return keyframes;
    }

    /**
     * Convert from PointF to float[] for multi-float setters along a Path.
     */
    private static class PointFToFloatArray extends TypeConverter<PointF, float[]> {
        private float[] mCoordinates = new float[2];

        public PointFToFloatArray() {
            super(PointF.class, float[].class);
        }

        @Override
        public float[] convert(PointF value) {
            mCoordinates[0] = value.x;
            mCoordinates[1] = value.y;
            return mCoordinates;
        }
    };

    /**
     * Convert from PointF to int[] for multi-int setters along a Path.
     */
    private static class PointFToIntArray extends TypeConverter<PointF, int[]> {
        private int[] mCoordinates = new int[2];

        public PointFToIntArray() {
            super(PointF.class, int[].class);
        }

        @Override
        public int[] convert(PointF value) {
            mCoordinates[0] = Math.round(value.x);
            mCoordinates[1] = Math.round(value.y);
            return mCoordinates;
        }
    };

    native static private long nGetIntMethod(Class targetClass, String methodName);
    native static private long nGetFloatMethod(Class targetClass, String methodName);
    native static private long nGetMultipleIntMethod(Class targetClass, String methodName,
            int numParams);
    native static private long nGetMultipleFloatMethod(Class targetClass, String methodName,
            int numParams);
    native static private void nCallIntMethod(Object target, long methodID, int arg);
    native static private void nCallFloatMethod(Object target, long methodID, float arg);
    native static private void nCallTwoIntMethod(Object target, long methodID, int arg1, int arg2);
    native static private void nCallFourIntMethod(Object target, long methodID, int arg1, int arg2,
            int arg3, int arg4);
    native static private void nCallMultipleIntMethod(Object target, long methodID, int[] args);
    native static private void nCallTwoFloatMethod(Object target, long methodID, float arg1,
            float arg2);
    native static private void nCallFourFloatMethod(Object target, long methodID, float arg1,
            float arg2, float arg3, float arg4);
    native static private void nCallMultipleFloatMethod(Object target, long methodID, float[] args);
}
