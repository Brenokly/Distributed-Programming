package com.climate.datas.utils.common.interfaces;

public interface MessageHandler {
    void sendTextMessage(String message);

    void sendJsonMessage(Object message);

    String receiveTextMessage();

    <T> T receiveJsonMessage(Class<T> clas);

    void close();
}