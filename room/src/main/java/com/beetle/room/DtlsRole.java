package com.beetle.room;

/** DTLS roles expected by the room server. */
public enum DtlsRole {
    SERVER("server"),
    CLIENT("client");

    private final String wireValue;

    DtlsRole(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
