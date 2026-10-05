package com.beetle.room;

/** Lifecycle states for one RoomClient instance. A closed instance cannot be restarted. */
public enum RoomSessionState {
    IDLE,
    CONNECTING,
    RECONNECTING,
    AUTHENTICATING,
    CREATING_TRANSPORTS,
    JOINING,
    JOINED,
    CLOSING,
    CLOSED,
    FAILED
}
