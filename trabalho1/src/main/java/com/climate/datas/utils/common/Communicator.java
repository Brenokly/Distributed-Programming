package com.climate.datas.utils.common;

import lombok.Getter;

import java.io.IOException;
import java.net.Socket;

@Getter
public class Communicator implements AutoCloseable {
    private final AbstractSocketConnection connection;
    private AbstractMessageHandler ioHandler;
    private final String name;

    public Communicator(String name) {
        this.connection = new AbstractSocketConnection(name);
        this.name = name;
    }

    public Communicator(Socket socket, String name) {
        this.connection = new AbstractSocketConnection(socket, name);
        this.name = name;
        if (connection.isConnected()) {
            createIOHandler();
        }
    }

    public void connect(String host, int port) {
        connection.connect(host, port);
        if (connection.isConnected()) {
            createIOHandler();
        }
    }

    public void createIOHandler() {
        try {
            if (ioHandler != null) {
                ioHandler.close();
            }
            ioHandler = new AbstractMessageHandler(connection.getSocket().getOutputStream(), connection.getSocket().getInputStream(), name);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao tentar abrir Fluxo de Dados!", e);
        }
    }

    public void disconnect() {
        if (ioHandler != null) {
            ioHandler.close();
            ioHandler = null;
        }
        connection.disconnect();
    }

    public void sendTextMessage(String message) {
        ioHandler.sendTextMessage(message);
    }

    public void sendJsonMessage(Object message) {
        ioHandler.sendJsonMessage(message);
    }

    public String receiveTextMessage() {
        return ioHandler.receiveTextMessage();
    }

    public <T> T receiveJsonMessage(Class<T> clas) {
        return ioHandler.receiveJsonMessage(clas);
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    public boolean isClosed() {
        return connection.isClosed();
    }

    public Socket getSocket() {
        return connection.getSocket();
    }

    @Override
    public void close() {
        disconnect();
    }
}