package com.beetle.conference.room;

import java.util.List;

public interface RoomClientObserver {
    void onConnect();
    void onDisconnect();
    void onClose();
    void onJoined(List<String> peers);
    void onPeer(String peerId);
    void onPeerClosed(String peerId);
}
