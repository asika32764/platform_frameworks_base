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
package android.hardware.camera2.marshal;

import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.utils.TypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Registry of supported marshalers; add new query-able marshalers or lookup existing ones.</p>
 */
public class MarshalRegistry {

    /**
     * Register a marshal queryable for the managed type {@code T}.
     *
     * <p>Multiple marshal queryables for the same managed type {@code T} may be registered;
     * this is desirable if they support different native types (e.g. marshaler 1 supports
     * {@code Integer <-> TYPE_INT32}, marshaler 2 supports {@code Integer <-> TYPE_BYTE}.</p>
     *
     * @param queryable a non-{@code null} marshal queryable that supports marshaling {@code T}
     */
    public static <T> void registerMarshalQueryable(MarshalQueryable<T> queryable) {
        sRegisteredMarshalQueryables.add(queryable);
    }

    /**
     * Lookup a marshaler between {@code T} and {@code nativeType}.
     *
     * <p>Marshalers are looked up in the order they were registered; earlier registered
     * marshal queriers get priority.</p>
     *
     * @param typeToken The compile-time type reference for {@code T}
     * @param nativeType The native type, e.g. {@link CameraMetadataNative#TYPE_BYTE TYPE_BYTE}
     * @return marshaler a non-{@code null} marshaler that supports marshaling the type combo
     *
     * @throws UnsupportedOperationException If no marshaler matching the args could be found
     */
    @SuppressWarnings("unchecked")
    public static <T> Marshaler<T> getMarshaler(TypeReference<T> typeToken, int nativeType) {
        // TODO: can avoid making a new token each time by code-genning
        // the list of type tokens and native types from the keys (at the call sites)
        MarshalToken<T> marshalToken = new MarshalToken<T>(typeToken, nativeType);

        /*
         * Marshalers are instantiated lazily once they are looked up; successive lookups
         * will not instantiate new marshalers.
         */
        Marshaler<T> marshaler =
                (Marshaler<T>) sMarshalerMap.get(marshalToken);

        if (sRegisteredMarshalQueryables.size() == 0) {
            throw new AssertionError("No available query marshalers registered");
        }

        if (marshaler == null) {
            // Query each marshaler to see if they support the native/managed type combination
            for (MarshalQueryable<?> potentialMarshaler : sRegisteredMarshalQueryables) {

                MarshalQueryable<T> castedPotential =
                        (MarshalQueryable<T>)potentialMarshaler;

                if (castedPotential.isTypeMappingSupported(typeToken, nativeType)) {
                    marshaler = castedPotential.createMarshaler(typeToken, nativeType);
                    break;
                }
            }
        }

        if (marshaler == null) {
            throw new UnsupportedOperationException(
                     "Could not find marshaler that matches the requested " +
                     "combination of type reference " +
                     typeToken + " and native type " +
                     MarshalHelpers.toStringNativeType(nativeType));
        }

        sMarshalerMap.put(marshalToken, marshaler);

        return marshaler;
    }

    private static class MarshalToken<T> {
        public MarshalToken(TypeReference<T> typeReference, int nativeType) {
            this.typeReference = typeReference;
            this.nativeType = nativeType;
        }

        final TypeReference<T> typeReference;
        final int nativeType;

        @Override
        public boolean equals(Object other) {
            if (other instanceof MarshalToken<?>) {
                MarshalToken<?> otherToken = (MarshalToken<?>)other;
                return typeReference.equals(otherToken.typeReference) &&
                        nativeType == otherToken.nativeType;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return typeReference.hashCode() ^ nativeType;
        }
    }

    private static List<MarshalQueryable<?>> sRegisteredMarshalQueryables =
            new ArrayList<MarshalQueryable<?>>();
    private static HashMap<MarshalToken<?>, Marshaler<?>> sMarshalerMap =
            new HashMap<MarshalToken<?>, Marshaler<?>>();

    private MarshalRegistry() {
        throw new AssertionError();
    }
}
