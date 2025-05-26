package com.climate.datas.loadbalancer;

import com.climate.datas.utils.ServerInfo;
import com.climate.datas.utils.common.Communicator;
import com.climate.datas.utils.drone.DatagramDrone;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    private String host;                            // Endereço do load balancer
    private final int port;                         // Porta do load balancer
    private ServerSocket balancerSocket;            // Socket do load balancer
    private volatile boolean running = false;       // Flag indicadora de execução
    private final ExecutorService threadPool;       // Pool de threads para tratar as conexões
    private final List<ServerInfo> servers;         // Info dos servidores
    private final Random random = new Random();
    private final AtomicInteger index = new AtomicInteger(0);

    public LoadBalancer() {
        try {
            this.host = InetAddress.getLocalHost().getHostAddress();
            System.out.println(this.host);
        } catch (UnknownHostException e) {
            this.host = "26.137.178.91";
        }
        this.port = 50000; // Porta para os drones
        threadPool = Executors.newCachedThreadPool();

        servers = List.of(new ServerInfo(host, 50001), new ServerInfo(host, 50002));

        start();
    }

    public void start() {
        try {
            this.balancerSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
            running = true;
            System.out.println("Load Balancer rodando em " + host + ":" + port);

            while (running) {
                try {
                    Socket droneSocket = balancerSocket.accept();
                    threadPool.submit(() -> handleDroneConnection(new Communicator(droneSocket, "LoadBalancer")));
                } catch (IOException e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Erro ao iniciar LoadBalancer", e);
        } finally {
            stop();
        }
    }

    private void handleDroneConnection(Communicator droneSocket) {

        if (droneSocket == null) {
            System.out.println("Drone desconectado.");
            return;
        }

        try {
            // Recebe os dados do drone
            DatagramDrone data = droneSocket.receiveJsonMessage(DatagramDrone.class);

            Communicator serverSocket = connectServer();

            if (serverSocket != null) {
                // Envia os dados para o servidor
                serverSocket.sendJsonMessage(data);
            } else {
                System.err.println("Erro ao conectar ao servidor.");
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar dados do drone: " + e.getMessage());
        } finally {
            if (droneSocket.isConnected()) {
                droneSocket.disconnect();
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (balancerSocket != null && !balancerSocket.isClosed()) {
                balancerSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        threadPool.shutdownNow();
    }

    public Communicator connectServer() {
        ServerInfo server = chooseServerRR();
        Socket serverSocket = null;

        try {
            serverSocket = new Socket(server.getHost(), server.getPort());
            return new Communicator(serverSocket, "LoadBalancer");
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
            return null;
        }
    }


    public ServerInfo chooseServerRandom() {
        return servers.get(random.nextInt(servers.size()));
    }

    public ServerInfo chooseServerRR() {
        // Distribuição por Round-Robin
        int i = Math.abs(index.getAndIncrement() % servers.size());
        return servers.get(i);
    }

}
