package com.climate.datas.datacenter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.climate.datas.utils.Loggable;
import com.climate.datas.utils.ServerInfo;
import com.climate.datas.utils.common.Communicator;
import com.climate.datas.utils.drone.DatagramDrone;

import lombok.Getter;

public class DataCenter implements AutoCloseable, Loggable {
    private final String host;                      // Endereço do load balancer
    private final int port;                         // Porta do load balancer
    private String hostServers;                     // Endereço dos servidores
    private InetSocketAddress grupo;                // Endereço do grupo multicast
    private MulticastSocket dataSocket;             // MulticastSocket
    private final ExecutorService threadPool;       // Pool de threads para tratar as conexões
    private final List<ServerInfo> servers;         // Info dos servidores
    private final List<Communicator> communicators; // Lista de comunicadores para enviar dados
    Random random = new Random();

    @Getter
    private volatile boolean running = false;       // Flag indicadora de execução

    public DataCenter() throws Exception {
        this.port = 49999;
        this.host = "230.0.0.1";
        this.hostServers = "10.10.71.58";
        this.threadPool = Executors.newVirtualThreadPerTaskExecutor();
        this.servers = List.of(new ServerInfo(hostServers, 50001), new ServerInfo(hostServers, 50002));
        this.communicators = new ArrayList<>();
        startCommunicationServers();
        startCommunicationDrones();
    }

    public void startCommunicationServers() throws Exception {
        servers.forEach(server -> {
            try {
                Communicator communicator = new Communicator(new Socket(server.getHost(), server.getPort()), "DataCenter");
                communicators.add(communicator);
            } catch (IOException e) {
                erro("Erro ao conectar com o servidor " + server.getHost() + ":" + server.getPort() + ": " + e.getMessage());
            }
        });

        if (communicators.isEmpty()) {
            erro("Nenhum servidor disponível para comunicação.");
            throw new Exception("Nenhum servidor disponível para comunicação.");
        }
    }

    public void startCommunicationDrones() throws Exception {
        try {
            dataSocket = new MulticastSocket(port);
            grupo = new InetSocketAddress(InetAddress.getByName(host), port);
            dataSocket.joinGroup(grupo, NetworkInterface.getByName("Ethernet"));
            running = true;
        } catch (Exception e) {
            throw new Exception("Não foi possível iniciar o DataCenter na porta " + port + " em " + host, e);
        }
    }

    public void start() throws IllegalStateException {
        try {
            while (running) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                dataSocket.receive(packet);

                threadPool.execute(() -> {
                    try {
                        handleDroneConnection(DatagramDrone.fromPacket(packet));
                    } catch (Exception e) {
                        erro("Erro ao processar dados do drone: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            info("Conexão encerrada ou erro inesperado no DataCenter: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void handleDroneConnection(DatagramDrone packet) {
        System.out.println("\nDados recebidos do Drone: " + packet);

        int chosenIndex = chosenServer(packet.getDroneId().getOrdinal());
        ServerInfo server = servers.get(chosenIndex);

        try {
            if (sendToCommunicator(chosenIndex, packet)) {
                info("Dados enviados para o servidor: " + server.getHost() + ":" + server.getPort());
            } else {
                erro("Servidor " + server.getHost() + ":" + server.getPort() + " não está conectado.");
                if (reconnectCommunicator(chosenIndex, server)) {
                    sendToCommunicator(chosenIndex, packet);
                    System.out.println("Dados reenviados para o servidor: " + server.getHost() + ":" + server.getPort());
                } else {
                    erro("Falha ao reconectar ao servidor " + server.getHost() + ":" + server.getPort());
                    removeServerAndCommunicator(chosenIndex);
                }
            }
        } catch (Exception e) {
            erro("Houve algum erro ao enviar os dados do drone para o servidor: " + e.getMessage());
        }
    }

    private int chosenServer(int idHasCode) {
        // Hash-based Load Balancing
        int serverIndex = random.nextInt(servers.size());

        System.out.println("\nHash do Drone: " + idHasCode + ", Servidor escolhido: " + serverIndex);

        // Pega o servidor escolhido para processar os dados
        return serverIndex;
    }

    private boolean sendToCommunicator(int index, DatagramDrone packet) {
        Communicator communicator = communicators.get(index);
        if (communicator != null && communicator.isConnected()) {
            communicator.sendJsonMessage(packet);
            return true;
        }
        return false;
    }

    private boolean reconnectCommunicator(int index, ServerInfo server) {
        try {
            Communicator newCommunicator = new Communicator(new Socket(server.getHost(), server.getPort()), "DataCenter");
            communicators.set(index, newCommunicator);
            return newCommunicator.isConnected();
        } catch (Exception e) {
            erro("Erro ao tentar reconectar: " + e.getMessage());
            return false;
        }
    }

    private void removeServerAndCommunicator(int index) {
        try {
            Communicator removedCommunicator = communicators.remove(index);
            if (removedCommunicator != null) {
                removedCommunicator.close();
            }
        } catch (Exception e) {
            erro("Erro ao fechar communicator: " + e.getMessage());
        }
        servers.remove(index);
    }

    @Override
    public void close() {
        running = false;
        if (dataSocket != null && !dataSocket.isClosed()) {
            try {
                dataSocket.leaveGroup(grupo, NetworkInterface.getByName("Ethernet"));
                dataSocket.close();
            } catch (Exception e) {
                erro("Erro ao fechar o socket do DataCenter: " + e.getMessage());
            }
        }
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
        }
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (
                executor;
                DataCenter dataCenter = new DataCenter()
        ) {
            dataCenter.startCommunicationDrones();
            executor.execute(dataCenter::start);
            LocalTime inicio = LocalTime.now().withNano(0);
            System.out.println("DataCenter iniciado. Aguardando dados dos drones...");

            // Executa por 3 minutos
            Thread.sleep(180000);

            System.out.println("Encerrando DataCenter...");
            dataCenter.close();

            LocalTime termino = LocalTime.now().withNano(0);

            Duration duracao = Duration.between(inicio, termino);

            System.out.println("Tempo de execução: " + duracao.getSeconds() + " segundos");

        } catch (Exception e) {
            System.err.println("Erro ao iniciar o DataCenter: " + e.getMessage());
        } finally {
            System.out.println("DataCenter finalizado.");
        }
    }
}
