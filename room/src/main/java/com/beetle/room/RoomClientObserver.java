package com.beetle.room;

import java.util.List;

public interface RoomClientObserver {
    //socket连接成功
    void onConnect();
    //连接断开，会自动重连
    void onDisconnect();

    //服务器内部接口返回错误，不会再自动重连
    void onError();

    //连接被主动关闭，不会自动重连
    void onClose();
    void onJoined(List<String> peers);
    void onPeer(String peerId);
    void onPeerClosed(String peerId);
}
