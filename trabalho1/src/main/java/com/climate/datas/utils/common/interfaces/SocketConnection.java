package com.climate.datas.utils.common.interfaces;

import java.net.Socket;

public interface SocketConnection {
    void connect();

    void connect(String host, int port);

    void disconnect();

    boolean isConnected();

    boolean isClosed();

    Socket getSocket();

    String getHost();

    void setHost(String host);

    int getPort();

    void setPort(int port);
}