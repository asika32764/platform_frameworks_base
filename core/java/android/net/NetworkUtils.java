/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.net;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;

import android.util.Log;
import android.util.Pair;


/**
 * Native methods for managing network interfaces.
 *
 * {@hide}
 */
public class NetworkUtils {

    private static final String TAG = "NetworkUtils";

    /** Bring the named network interface up. */
    public native static int enableInterface(String interfaceName);

    /** Bring the named network interface down. */
    public native static int disableInterface(String interfaceName);

    /** Setting bit 0 indicates reseting of IPv4 addresses required */
    public static final int RESET_IPV4_ADDRESSES = 0x01;

    /** Setting bit 1 indicates reseting of IPv4 addresses required */
    public static final int RESET_IPV6_ADDRESSES = 0x02;

    /** Reset all addresses */
    public static final int RESET_ALL_ADDRESSES = RESET_IPV4_ADDRESSES | RESET_IPV6_ADDRESSES;

    /**
     * Reset IPv6 or IPv4 sockets that are connected via the named interface.
     *
     * @param interfaceName is the interface to reset
     * @param mask {@see #RESET_IPV4_ADDRESSES} and {@see #RESET_IPV6_ADDRESSES}
     */
    public native static int resetConnections(String interfaceName, int mask);

    /**
     * Start the DHCP client daemon, in order to have it request addresses
     * for the named interface, and then configure the interface with those
     * addresses. This call blocks until it obtains a result (either success
     * or failure) from the daemon.
     * @param interfaceName the name of the interface to configure
     * @param dhcpResults if the request succeeds, this object is filled in with
     * the IP address information.
     * @return {@code true} for success, {@code false} for failure
     */
    public native static boolean runDhcp(String interfaceName, DhcpResults dhcpResults);

    /**
     * Initiate renewal on the Dhcp client daemon. This call blocks until it obtains
     * a result (either success or failure) from the daemon.
     * @param interfaceName the name of the interface to configure
     * @param dhcpResults if the request succeeds, this object is filled in with
     * the IP address information.
     * @return {@code true} for success, {@code false} for failure
     */
    public native static boolean runDhcpRenew(String interfaceName, DhcpResults dhcpResults);

    /**
     * Shut down the DHCP client daemon.
     * @param interfaceName the name of the interface for which the daemon
     * should be stopped
     * @return {@code true} for success, {@code false} for failure
     */
    public native static boolean stopDhcp(String interfaceName);

    /**
     * Release the current DHCP lease.
     * @param interfaceName the name of the interface for which the lease should
     * be released
     * @return {@code true} for success, {@code false} for failure
     */
    public native static boolean releaseDhcpLease(String interfaceName);

    /**
     * Return the last DHCP-related error message that was recorded.
     * <p/>NOTE: This string is not localized, but currently it is only
     * used in logging.
     * @return the most recent error message, if any
     */
    public native static String getDhcpError();

    /**
     * Set the SO_MARK of {@code socketfd} to {@code mark}
     */
    public native static void markSocket(int socketfd, int mark);

    /**
     * Binds the current process to the network designated by {@code netId}.  All sockets created
     * in the future (and not explicitly bound via a bound {@link SocketFactory} (see
     * {@link Network#getSocketFactory}) will be bound to this network.  Note that if this
     * {@code Network} ever disconnects all sockets created in this way will cease to work.  This
     * is by design so an application doesn't accidentally use sockets it thinks are still bound to
     * a particular {@code Network}.
     */
    public native static void bindProcessToNetwork(int netId);

    /**
     * Clear any process specific {@code Network} binding.  This reverts a call to
     * {@link #bindProcessToNetwork}.
     */
    public native static void unbindProcessToNetwork();

    /**
     * Return the netId last passed to {@link #bindProcessToNetwork}, or NETID_UNSET if
     * {@link #unbindProcessToNetwork} has been called since {@link #bindProcessToNetwork}.
     */
    public native static int getNetworkBoundToProcess();

    /**
     * Binds host resolutions performed by this process to the network designated by {@code netId}.
     * {@link #bindProcessToNetwork} takes precedence over this setting.
     *
     * @deprecated This is strictly for legacy usage to support startUsingNetworkFeature().
     */
    public native static void bindProcessToNetworkForHostResolution(int netId);

    /**
     * Clears any process specific {@link Network} binding for host resolution.  This does
     * not clear bindings enacted via {@link #bindProcessToNetwork}.
     *
     * @deprecated This is strictly for legacy usage to support startUsingNetworkFeature().
     */
    public native static void unbindProcessToNetworkForHostResolution();

    /**
     * Explicitly binds {@code socketfd} to the network designated by {@code netId}.  This
     * overrides any binding via {@link #bindProcessToNetwork}.
     */
    public native static void bindSocketToNetwork(int socketfd, int netId);

    /**
     * Convert a IPv4 address from an integer to an InetAddress.
     * @param hostAddress an int corresponding to the IPv4 address in network byte order
     */
    public static InetAddress intToInetAddress(int hostAddress) {
        byte[] addressBytes = { (byte)(0xff & hostAddress),
                                (byte)(0xff & (hostAddress >> 8)),
                                (byte)(0xff & (hostAddress >> 16)),
                                (byte)(0xff & (hostAddress >> 24)) };

        try {
           return InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
           throw new AssertionError();
        }
    }

    /**
     * Convert a IPv4 address from an InetAddress to an integer
     * @param inetAddr is an InetAddress corresponding to the IPv4 address
     * @return the IP address as an integer in network byte order
     */
    public static int inetAddressToInt(Inet4Address inetAddr)
            throws IllegalArgumentException {
        byte [] addr = inetAddr.getAddress();
        return ((addr[3] & 0xff) << 24) | ((addr[2] & 0xff) << 16) |
                ((addr[1] & 0xff) << 8) | (addr[0] & 0xff);
    }

    /**
     * Convert a network prefix length to an IPv4 netmask integer
     * @param prefixLength
     * @return the IPv4 netmask as an integer in network byte order
     */
    public static int prefixLengthToNetmaskInt(int prefixLength)
            throws IllegalArgumentException {
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("Invalid prefix length (0 <= prefix <= 32)");
        }
        int value = 0xffffffff << (32 - prefixLength);
        return Integer.reverseBytes(value);
    }

    /**
     * Convert a IPv4 netmask integer to a prefix length
     * @param netmask as an integer in network byte order
     * @return the network prefix length
     */
    public static int netmaskIntToPrefixLength(int netmask) {
        return Integer.bitCount(netmask);
    }

    /**
     * Create an InetAddress from a string where the string must be a standard
     * representation of a V4 or V6 address.  Avoids doing a DNS lookup on failure
     * but it will throw an IllegalArgumentException in that case.
     * @param addrString
     * @return the InetAddress
     * @hide
     */
    public static InetAddress numericToInetAddress(String addrString)
            throws IllegalArgumentException {
        return InetAddress.parseNumericAddress(addrString);
    }

    /**
     *  Masks a raw IP address byte array with the specified prefix length.
     */
    public static void maskRawAddress(byte[] array, int prefixLength) {
        if (prefixLength < 0 || prefixLength > array.length * 8) {
            throw new RuntimeException("IP address with " + array.length +
                    " bytes has invalid prefix length " + prefixLength);
        }

        int offset = prefixLength / 8;
        int remainder = prefixLength % 8;
        byte mask = (byte)(0xFF << (8 - remainder));

        if (offset < array.length) array[offset] = (byte)(array[offset] & mask);

        offset++;

        for (; offset < array.length; offset++) {
            array[offset] = 0;
        }
    }

    /**
     * Get InetAddress masked with prefixLength.  Will never return null.
     * @param address the IP address to mask with
     * @param prefixLength the prefixLength used to mask the IP
     */
    public static InetAddress getNetworkPart(InetAddress address, int prefixLength) {
        byte[] array = address.getAddress();
        maskRawAddress(array, prefixLength);

        InetAddress netPart = null;
        try {
            netPart = InetAddress.getByAddress(array);
        } catch (UnknownHostException e) {
            throw new RuntimeException("getNetworkPart error - " + e.toString());
        }
        return netPart;
    }

    /**
     * Utility method to parse strings such as "192.0.2.5/24" or "2001:db8::cafe:d00d/64".
     * @hide
     */
    public static Pair<InetAddress, Integer> parseIpAndMask(String ipAndMaskString) {
        InetAddress address = null;
        int prefixLength = -1;
        try {
            String[] pieces = ipAndMaskString.split("/", 2);
            prefixLength = Integer.parseInt(pieces[1]);
            address = InetAddress.parseNumericAddress(pieces[0]);
        } catch (NullPointerException e) {            // Null string.
        } catch (ArrayIndexOutOfBoundsException e) {  // No prefix length.
        } catch (NumberFormatException e) {           // Non-numeric prefix.
        } catch (IllegalArgumentException e) {        // Invalid IP address.
        }

        if (address == null || prefixLength == -1) {
            throw new IllegalArgumentException("Invalid IP address and mask " + ipAndMaskString);
        }

        return new Pair<InetAddress, Integer>(address, prefixLength);
    }

    /**
     * Check if IP address type is consistent between two InetAddress.
     * @return true if both are the same type.  False otherwise.
     */
    public static boolean addressTypeMatches(InetAddress left, InetAddress right) {
        return (((left instanceof Inet4Address) && (right instanceof Inet4Address)) ||
                ((left instanceof Inet6Address) && (right instanceof Inet6Address)));
    }

    /**
     * Convert a 32 char hex string into a Inet6Address.
     * throws a runtime exception if the string isn't 32 chars, isn't hex or can't be
     * made into an Inet6Address
     * @param addrHexString a 32 character hex string representing an IPv6 addr
     * @return addr an InetAddress representation for the string
     */
    public static InetAddress hexToInet6Address(String addrHexString)
            throws IllegalArgumentException {
        try {
            return numericToInetAddress(String.format(Locale.US, "%s:%s:%s:%s:%s:%s:%s:%s",
                    addrHexString.substring(0,4),   addrHexString.substring(4,8),
                    addrHexString.substring(8,12),  addrHexString.substring(12,16),
                    addrHexString.substring(16,20), addrHexString.substring(20,24),
                    addrHexString.substring(24,28), addrHexString.substring(28,32)));
        } catch (Exception e) {
            Log.e("NetworkUtils", "error in hexToInet6Address(" + addrHexString + "): " + e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Create a string array of host addresses from a collection of InetAddresses
     * @param addrs a Collection of InetAddresses
     * @return an array of Strings containing their host addresses
     */
    public static String[] makeStrings(Collection<InetAddress> addrs) {
        String[] result = new String[addrs.size()];
        int i = 0;
        for (InetAddress addr : addrs) {
            result[i++] = addr.getHostAddress();
        }
        return result;
    }

    /**
     * Trim leading zeros from IPv4 address strings
     * Our base libraries will interpret that as octel..
     * Must leave non v4 addresses and host names alone.
     * For example, 192.168.000.010 -> 192.168.0.10
     * TODO - fix base libraries and remove this function
     * @param addr a string representing an ip addr
     * @return a string propertly trimmed
     */
    public static String trimV4AddrZeros(String addr) {
        if (addr == null) return null;
        String[] octets = addr.split("\\.");
        if (octets.length != 4) return addr;
        StringBuilder builder = new StringBuilder(16);
        String result = null;
        for (int i = 0; i < 4; i++) {
            try {
                if (octets[i].length() > 3) return addr;
                builder.append(Integer.parseInt(octets[i]));
            } catch (NumberFormatException e) {
                return addr;
            }
            if (i < 3) builder.append('.');
        }
        result = builder.toString();
        return result;
    }
}
