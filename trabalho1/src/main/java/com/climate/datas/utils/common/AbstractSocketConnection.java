package com.climate.datas.utils.common;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import com.climate.datas.utils.Loggable;
import com.climate.datas.utils.common.interfaces.SocketConnection;
import lombok.Getter;
import lombok.Setter;

@Getter
public class AbstractSocketConnection implements SocketConnection, Loggable {
    @Setter
    private int port;
    @Setter
    private String host;
    @Setter
    private String name;
    private Socket socket;
    private InetAddress inet;
    @Getter
    static private int connections = 0;

    public AbstractSocketConnection(String name) {
        this.name = name;
        connections++;
    }

    public AbstractSocketConnection(Socket socket, String name) {
        this.socket = socket;
        this.name = name;
        host = socket.getInetAddress().getHostAddress();
        port = socket.getPort();
        inet = socket.getInetAddress();
        connections++;
        info(name + " conectado a " + host + ":" + port);
    }

    @Override
    public synchronized void connect() {
        if (isConnected()) {
            if (inet.getHostName().equals(host) && socket.getPort() == port) {
                warn(name + " ja está conectado a " + inet.getHostName() + ":" + port);
                return;
            } else {
                warn(name + " já está conectado a " + inet.getHostName() + ". Desconectando antes de conectar...");
                disconnect();
            }
        }

        try {
            socket = new Socket(host, port);
            inet = socket.getInetAddress();

            info(name + " conectado a " + host + ":" + port);
        } catch (Exception e) {
            erro("Erro ao tentar conectar " + name + " a " + host + ":" + port);
        }
    }

    @Override
    public void connect(String host, int port) {
        this.host = host;
        this.port = port;
        connect();
    }

    @Override
    public synchronized void disconnect() {
        try {
            if (socket != null && socket.isConnected()) {
                socket.close();
                socket = null;
                info("Conexão de " + name + " fechada com o " + inet.getHostName() + ".");
            }
        } catch (IOException e) {
            erro("Erro ao " + name + " tentar desconectar: " + e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public Socket getSocket() {
        return socket;
    }
}