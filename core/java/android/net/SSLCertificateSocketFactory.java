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

import android.os.SystemProperties;
import android.util.Log;
import com.android.org.conscrypt.OpenSSLContextImpl;
import com.android.org.conscrypt.OpenSSLSocketImpl;
import com.android.org.conscrypt.SSLClientSessionCache;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SSLSocketFactory implementation with several extra features:
 *
 * <ul>
 * <li>Timeout specification for SSL handshake operations
 * <li>Hostname verification in most cases (see WARNINGs below)
 * <li>Optional SSL session caching with {@link SSLSessionCache}
 * <li>Optionally bypass all SSL certificate checks
 * </ul>
 *
 * The handshake timeout does not apply to actual TCP socket connection.
 * If you want a connection timeout as well, use {@link #createSocket()}
 * and {@link Socket#connect(SocketAddress, int)}, after which you
 * must verify the identity of the server you are connected to.
 *
 * <p class="caution"><b>Most {@link SSLSocketFactory} implementations do not
 * verify the server's identity, allowing man-in-the-middle attacks.</b>
 * This implementation does check the server's certificate hostname, but only
 * for createSocket variants that specify a hostname.  When using methods that
 * use {@link InetAddress} or which return an unconnected socket, you MUST
 * verify the server's identity yourself to ensure a secure connection.</p>
 *
 * <p>One way to verify the server's identity is to use
 * {@link HttpsURLConnection#getDefaultHostnameVerifier()} to get a
 * {@link HostnameVerifier} to verify the certificate hostname.
 *
 * <p>On development devices, "setprop socket.relaxsslcheck yes" bypasses all
 * SSL certificate and hostname checks for testing purposes.  This setting
 * requires root access.
 */
public class SSLCertificateSocketFactory extends SSLSocketFactory {
    private static final String TAG = "SSLCertificateSocketFactory";

    private static final TrustManager[] INSECURE_TRUST_MANAGER = new TrustManager[] {
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return null; }
            public void checkClientTrusted(X509Certificate[] certs, String authType) { }
            public void checkServerTrusted(X509Certificate[] certs, String authType) { }
        }
    };

    private SSLSocketFactory mInsecureFactory = null;
    private SSLSocketFactory mSecureFactory = null;
    private TrustManager[] mTrustManagers = null;
    private KeyManager[] mKeyManagers = null;
    private byte[] mNpnProtocols = null;
    private byte[] mAlpnProtocols = null;
    private PrivateKey mChannelIdPrivateKey = null;

    private final int mHandshakeTimeoutMillis;
    private final SSLClientSessionCache mSessionCache;
    private final boolean mSecure;

    /** @deprecated Use {@link #getDefault(int)} instead. */
    @Deprecated
    public SSLCertificateSocketFactory(int handshakeTimeoutMillis) {
        this(handshakeTimeoutMillis, null, true);
    }

    private SSLCertificateSocketFactory(
            int handshakeTimeoutMillis, SSLSessionCache cache, boolean secure) {
        mHandshakeTimeoutMillis = handshakeTimeoutMillis;
        mSessionCache = cache == null ? null : cache.mSessionCache;
        mSecure = secure;
    }

    /**
     * Returns a new socket factory instance with an optional handshake timeout.
     *
     * @param handshakeTimeoutMillis to use for SSL connection handshake, or 0
     *         for none.  The socket timeout is reset to 0 after the handshake.
     * @return a new SSLSocketFactory with the specified parameters
     */
    public static SocketFactory getDefault(int handshakeTimeoutMillis) {
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis, null, true);
    }

    /**
     * Returns a new socket factory instance with an optional handshake timeout
     * and SSL session cache.
     *
     * @param handshakeTimeoutMillis to use for SSL connection handshake, or 0
     *         for none.  The socket timeout is reset to 0 after the handshake.
     * @param cache The {@link SSLSessionCache} to use, or null for no cache.
     * @return a new SSLSocketFactory with the specified parameters
     */
    public static SSLSocketFactory getDefault(int handshakeTimeoutMillis, SSLSessionCache cache) {
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis, cache, true);
    }

    /**
     * Returns a new instance of a socket factory with all SSL security checks
     * disabled, using an optional handshake timeout and SSL session cache.
     *
     * <p class="caution"><b>Warning:</b> Sockets created using this factory
     * are vulnerable to man-in-the-middle attacks!</p>. The caller must implement
     * its own verification.
     *
     * @param handshakeTimeoutMillis to use for SSL connection handshake, or 0
     *         for none.  The socket timeout is reset to 0 after the handshake.
     * @param cache The {@link SSLSessionCache} to use, or null for no cache.
     * @return an insecure SSLSocketFactory with the specified parameters
     */
    public static SSLSocketFactory getInsecure(int handshakeTimeoutMillis, SSLSessionCache cache) {
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis, cache, false);
    }

    /**
     * Returns a socket factory (also named SSLSocketFactory, but in a different
     * namespace) for use with the Apache HTTP stack.
     *
     * @param handshakeTimeoutMillis to use for SSL connection handshake, or 0
     *         for none.  The socket timeout is reset to 0 after the handshake.
     * @param cache The {@link SSLSessionCache} to use, or null for no cache.
     * @return a new SocketFactory with the specified parameters
     */
    public static org.apache.http.conn.ssl.SSLSocketFactory getHttpSocketFactory(
            int handshakeTimeoutMillis, SSLSessionCache cache) {
        return new org.apache.http.conn.ssl.SSLSocketFactory(
                new SSLCertificateSocketFactory(handshakeTimeoutMillis, cache, true));
    }

    /**
     * Verify the hostname of the certificate used by the other end of a
     * connected socket.  You MUST call this if you did not supply a hostname
     * to {@link #createSocket()}.  It is harmless to call this method
     * redundantly if the hostname has already been verified.
     *
     * <p>Wildcard certificates are allowed to verify any matching hostname,
     * so "foo.bar.example.com" is verified if the peer has a certificate
     * for "*.example.com".
     *
     * @param socket An SSL socket which has been connected to a server
     * @param hostname The expected hostname of the remote server
     * @throws IOException if something goes wrong handshaking with the server
     * @throws SSLPeerUnverifiedException if the server cannot prove its identity
     *
     * @hide
     */
    public static void verifyHostname(Socket socket, String hostname) throws IOException {
        if (!(socket instanceof SSLSocket)) {
            throw new IllegalArgumentException("Attempt to verify non-SSL socket");
        }

        if (!isSslCheckRelaxed()) {
            // The code at the start of OpenSSLSocketImpl.startHandshake()
            // ensures that the call is idempotent, so we can safely call it.
            SSLSocket ssl = (SSLSocket) socket;
            ssl.startHandshake();

            SSLSession session = ssl.getSession();
            if (session == null) {
                throw new SSLException("Cannot verify SSL socket without session");
            }
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)) {
                throw new SSLPeerUnverifiedException("Cannot verify hostname: " + hostname);
            }
        }
    }

    private SSLSocketFactory makeSocketFactory(
            KeyManager[] keyManagers, TrustManager[] trustManagers) {
        try {
            OpenSSLContextImpl sslContext = new OpenSSLContextImpl();
            sslContext.engineInit(keyManagers, trustManagers, null);
            sslContext.engineGetClientSessionContext().setPersistentCache(mSessionCache);
            return sslContext.engineGetSocketFactory();
        } catch (KeyManagementException e) {
            Log.wtf(TAG, e);
            return (SSLSocketFactory) SSLSocketFactory.getDefault();  // Fallback
        }
    }

    private static boolean isSslCheckRelaxed() {
        return "1".equals(SystemProperties.get("ro.debuggable")) &&
            "yes".equals(SystemProperties.get("socket.relaxsslcheck"));
    }

    private synchronized SSLSocketFactory getDelegate() {
        // Relax the SSL check if instructed (for this factory, or systemwide)
        if (!mSecure || isSslCheckRelaxed()) {
            if (mInsecureFactory == null) {
                if (mSecure) {
                    Log.w(TAG, "*** BYPASSING SSL SECURITY CHECKS (socket.relaxsslcheck=yes) ***");
                }
                mInsecureFactory = makeSocketFactory(mKeyManagers, INSECURE_TRUST_MANAGER);
            }
            return mInsecureFactory;
        } else {
            if (mSecureFactory == null) {
                mSecureFactory = makeSocketFactory(mKeyManagers, mTrustManagers);
            }
            return mSecureFactory;
        }
    }

    /**
     * Sets the {@link TrustManager}s to be used for connections made by this factory.
     */
    public void setTrustManagers(TrustManager[] trustManager) {
        mTrustManagers = trustManager;

        // Clear out all cached secure factories since configurations have changed.
        mSecureFactory = null;
        // Note - insecure factories only ever use the INSECURE_TRUST_MANAGER so they need not
        // be cleared out here.
    }

    /**
     * Sets the <a href="http://technotes.googlecode.com/git/nextprotoneg.html">Next
     * Protocol Negotiation (NPN)</a> protocols that this peer is interested in.
     *
     * <p>For servers this is the sequence of protocols to advertise as
     * supported, in order of preference. This list is sent unencrypted to
     * all clients that support NPN.
     *
     * <p>For clients this is a list of supported protocols to match against the
     * server's list. If there is no protocol supported by both client and
     * server then the first protocol in the client's list will be selected.
     * The order of the client's protocols is otherwise insignificant.
     *
     * @param npnProtocols a non-empty list of protocol byte arrays. All arrays
     *     must be non-empty and of length less than 256.
     */
    public void setNpnProtocols(byte[][] npnProtocols) {
        this.mNpnProtocols = toLengthPrefixedList(npnProtocols);
    }

    /**
     * Sets the
     * <a href="http://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg-01">
     * Application Layer Protocol Negotiation (ALPN)</a> protocols that this peer
     * is interested in.
     *
     * <p>For servers this is the sequence of protocols to advertise as
     * supported, in order of preference. This list is sent unencrypted to
     * all clients that support ALPN.
     *
     * <p>For clients this is a list of supported protocols to match against the
     * server's list. If there is no protocol supported by both client and
     * server then the first protocol in the client's list will be selected.
     * The order of the client's protocols is otherwise insignificant.
     *
     * @param protocols a non-empty list of protocol byte arrays. All arrays
     *     must be non-empty and of length less than 256.
     * @hide
     */
    public void setAlpnProtocols(byte[][] protocols) {
        this.mAlpnProtocols = toLengthPrefixedList(protocols);
    }

    /**
     * Returns an array containing the concatenation of length-prefixed byte
     * strings.
     */
    static byte[] toLengthPrefixedList(byte[]... items) {
        if (items.length == 0) {
            throw new IllegalArgumentException("items.length == 0");
        }
        int totalLength = 0;
        for (byte[] s : items) {
            if (s.length == 0 || s.length > 255) {
                throw new IllegalArgumentException("s.length == 0 || s.length > 255: " + s.length);
            }
            totalLength += 1 + s.length;
        }
        byte[] result = new byte[totalLength];
        int pos = 0;
        for (byte[] s : items) {
            result[pos++] = (byte) s.length;
            for (byte b : s) {
                result[pos++] = b;
            }
        }
        return result;
    }

    /**
     * Returns the <a href="http://technotes.googlecode.com/git/nextprotoneg.html">Next
     * Protocol Negotiation (NPN)</a> protocol selected by client and server, or
     * null if no protocol was negotiated.
     *
     * @param socket a socket created by this factory.
     * @throws IllegalArgumentException if the socket was not created by this factory.
     */
    public byte[] getNpnSelectedProtocol(Socket socket) {
        return castToOpenSSLSocket(socket).getNpnSelectedProtocol();
    }

    /**
     * Returns the
     * <a href="http://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg-01">Application
     * Layer Protocol Negotiation (ALPN)</a> protocol selected by client and server, or null
     * if no protocol was negotiated.
     *
     * @param socket a socket created by this factory.
     * @throws IllegalArgumentException if the socket was not created by this factory.
     * @hide
     */
    public byte[] getAlpnSelectedProtocol(Socket socket) {
        return castToOpenSSLSocket(socket).getAlpnSelectedProtocol();
    }

    /**
     * Sets the {@link KeyManager}s to be used for connections made by this factory.
     */
    public void setKeyManagers(KeyManager[] keyManagers) {
        mKeyManagers = keyManagers;

        // Clear out any existing cached factories since configurations have changed.
        mSecureFactory = null;
        mInsecureFactory = null;
    }

    /**
     * Sets the private key to be used for TLS Channel ID by connections made by this
     * factory.
     *
     * @param privateKey private key (enables TLS Channel ID) or {@code null} for no key (disables
     *        TLS Channel ID). The private key has to be an Elliptic Curve (EC) key based on the
     *        NIST P-256 curve (aka SECG secp256r1 or ANSI X9.62 prime256v1).
     *
     * @hide
     */
    public void setChannelIdPrivateKey(PrivateKey privateKey) {
        mChannelIdPrivateKey = privateKey;
    }

    /**
     * Enables <a href="http://tools.ietf.org/html/rfc5077#section-3.2">session ticket</a>
     * support on the given socket.
     *
     * @param socket a socket created by this factory
     * @param useSessionTickets {@code true} to enable session ticket support on this socket.
     * @throws IllegalArgumentException if the socket was not created by this factory.
     */
    public void setUseSessionTickets(Socket socket, boolean useSessionTickets) {
        castToOpenSSLSocket(socket).setUseSessionTickets(useSessionTickets);
    }

    /**
     * Turns on <a href="http://tools.ietf.org/html/rfc6066#section-3">Server
     * Name Indication (SNI)</a> on a given socket.
     *
     * @param socket a socket created by this factory.
     * @param hostName the desired SNI hostname, null to disable.
     * @throws IllegalArgumentException if the socket was not created by this factory.
     */
    public void setHostname(Socket socket, String hostName) {
        castToOpenSSLSocket(socket).setHostname(hostName);
    }

    /**
     * Sets this socket's SO_SNDTIMEO write timeout in milliseconds.
     * Use 0 for no timeout.
     * To take effect, this option must be set before the blocking method was called.
     *
     * @param socket a socket created by this factory.
     * @param timeout the desired write timeout in milliseconds.
     * @throws IllegalArgumentException if the socket was not created by this factory.
     *
     * @hide
     */
    public void setSoWriteTimeout(Socket socket, int writeTimeoutMilliseconds)
            throws SocketException {
        castToOpenSSLSocket(socket).setSoWriteTimeout(writeTimeoutMilliseconds);
    }

    private static OpenSSLSocketImpl castToOpenSSLSocket(Socket socket) {
        if (!(socket instanceof OpenSSLSocketImpl)) {
            throw new IllegalArgumentException("Socket not created by this factory: "
                    + socket);
        }

        return (OpenSSLSocketImpl) socket;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method verifies the peer's certificate hostname after connecting
     * (unless created with {@link #getInsecure(int, SSLSessionCache)}).
     */
    @Override
    public Socket createSocket(Socket k, String host, int port, boolean close) throws IOException {
        OpenSSLSocketImpl s = (OpenSSLSocketImpl) getDelegate().createSocket(k, host, port, close);
        s.setNpnProtocols(mNpnProtocols);
        s.setAlpnProtocols(mAlpnProtocols);
        s.setHandshakeTimeout(mHandshakeTimeoutMillis);
        s.setChannelIdPrivateKey(mChannelIdPrivateKey);
        s.setHostname(host);
        if (mSecure) {
            verifyHostname(s, host);
        }
        return s;
    }

    /**
     * Creates a new socket which is not connected to any remote host.
     * You must use {@link Socket#connect} to connect the socket.
     *
     * <p class="caution"><b>Warning:</b> Hostname verification is not performed
     * with this method.  You MUST verify the server's identity after connecting
     * the socket to avoid man-in-the-middle attacks.</p>
     */
    @Override
    public Socket createSocket() throws IOException {
        OpenSSLSocketImpl s = (OpenSSLSocketImpl) getDelegate().createSocket();
        s.setNpnProtocols(mNpnProtocols);
        s.setAlpnProtocols(mAlpnProtocols);
        s.setHandshakeTimeout(mHandshakeTimeoutMillis);
        s.setChannelIdPrivateKey(mChannelIdPrivateKey);
        return s;
    }

    /**
     * {@inheritDoc}
     *
     * <p class="caution"><b>Warning:</b> Hostname verification is not performed
     * with this method.  You MUST verify the server's identity after connecting
     * the socket to avoid man-in-the-middle attacks.</p>
     */
    @Override
    public Socket createSocket(InetAddress addr, int port, InetAddress localAddr, int localPort)
            throws IOException {
        OpenSSLSocketImpl s = (OpenSSLSocketImpl) getDelegate().createSocket(
                addr, port, localAddr, localPort);
        s.setNpnProtocols(mNpnProtocols);
        s.setAlpnProtocols(mAlpnProtocols);
        s.setHandshakeTimeout(mHandshakeTimeoutMillis);
        s.setChannelIdPrivateKey(mChannelIdPrivateKey);
        return s;
    }

    /**
     * {@inheritDoc}
     *
     * <p class="caution"><b>Warning:</b> Hostname verification is not performed
     * with this method.  You MUST verify the server's identity after connecting
     * the socket to avoid man-in-the-middle attacks.</p>
     */
    @Override
    public Socket createSocket(InetAddress addr, int port) throws IOException {
        OpenSSLSocketImpl s = (OpenSSLSocketImpl) getDelegate().createSocket(addr, port);
        s.setNpnProtocols(mNpnProtocols);
        s.setAlpnProtocols(mAlpnProtocols);
        s.setHandshakeTimeout(mHandshakeTimeoutMillis);
        s.setChannelIdPrivateKey(mChannelIdPrivateKey);
        return s;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method verifies the peer's certificate hostname after connecting
     * (unless created with {@link #getInsecure(int, SSLSessionCache)}).
     */
    @Override
    public Socket createSocket(String host, int port, InetAddress localAddr, int localPort)
            throws IOException {
        OpenSSLSocketImpl s = (OpenSSLSocketImpl) getDelegate().createSocket(
                host, port, localAddr, localPort);
        s.setNpnProtocols(mNpnProtocols);
        s.setAlpnProtocols(mAlpnProtocols);
        s.setHandshakeTimeout(mHandshakeTimeoutMillis);
        s.setChannelIdPrivateKey(mChannelIdPrivateKey);
        if (mSecure) {
            verifyHostname(s, host);
        }
        return s;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method verifies the peer's certificate hostname after connecting
     * (unless created with {@link #getInsecure(int, SSLSessionCache)}).
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException {
        OpenSSLSocketImpl s = (OpenSSLSocketImpl) getDelegate().createSocket(host, port);
        s.setNpnProtocols(mNpnProtocols);
        s.setAlpnProtocols(mAlpnProtocols);
        s.setHandshakeTimeout(mHandshakeTimeoutMillis);
        s.setChannelIdPrivateKey(mChannelIdPrivateKey);
        if (mSecure) {
            verifyHostname(s, host);
        }
        return s;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return getDelegate().getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return getDelegate().getSupportedCipherSuites();
    }
}
