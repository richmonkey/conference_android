package com.beetle.room;

import android.content.Context;
import android.util.Log;

import org.mediasoup.MediaSoupClient;
import org.webrtc.PeerConnectionFactory;

/**
 * Process-wide initialization for the WebRTC and mediasoup runtimes.
 *
 * <p>Call {@link #initialize(Context)} once from the application's
 * {@code Application.onCreate()}. Calls after successful initialization are
 * harmless. A failed initialization is retained and exposed so callers do not
 * accidentally create a client backed by a partially initialized SDK.</p>
 */
public final class RoomSdk {
    private static final String TAG = "RoomSdk";

    public enum InitializationState {
        UNINITIALIZED,
        INITIALIZED,
        FAILED
    }

    private static InitializationState state = InitializationState.UNINITIALIZED;
    private static Throwable initializationFailure;

    private RoomSdk() {
    }

    public static synchronized void initialize(Context context) {
        if (state == InitializationState.INITIALIZED) {
            return;
        }
        if (state == InitializationState.FAILED) {
            throw new IllegalStateException("Room SDK initialization previously failed", initializationFailure);
        }

        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }

        try {
            Log.i(TAG, "Initializing WebRTC and mediasoup");
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                            .createInitializationOptions());
            MediaSoupClient.initialize();
            Log.i(TAG, "mediasoup version: " + MediaSoupClient.version());
            state = InitializationState.INITIALIZED;
        } catch (RuntimeException | LinkageError exception) {
            initializationFailure = exception;
            state = InitializationState.FAILED;
            Log.e(TAG, "Room SDK initialization failed", exception);
            throw new IllegalStateException("Unable to initialize Room SDK", exception);
        }
    }

    public static synchronized InitializationState getInitializationState() {
        return state;
    }

    public static synchronized Throwable getInitializationFailure() {
        return initializationFailure;
    }

    static synchronized void requireInitialized() {
        if (state != InitializationState.INITIALIZED) {
            String message = state == InitializationState.FAILED
                    ? "Room SDK initialization failed"
                    : "RoomSdk.initialize(applicationContext) must be called from Application.onCreate()";
            throw new IllegalStateException(message, initializationFailure);
        }
    }
}
