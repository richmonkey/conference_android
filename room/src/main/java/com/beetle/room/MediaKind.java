package com.beetle.room;

/** Media kinds understood by the room protocol and mediasoup. */
public enum MediaKind {
    AUDIO("audio"),
    VIDEO("video");

    private final String wireValue;

    MediaKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static MediaKind fromWireValue(String value) {
        for (MediaKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unsupported media kind: " + value);
    }
}
