package com.climate.datas.server;

import com.climate.datas.utils.ServerInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Server {
    private String host;                            // Endereço do servidor
    private final int port;                         // Porta do servidor
    private ServerSocket serverSocket;              // Socket do server atual
    private volatile boolean running = false;       // Flag indicadora de execução
    private final ExecutorService threadPool;       // Pool de threads para tratar as conexões
    private final String grupo = "225.7.8.9";       // Grupo multicast
    private final Random random = new Random();
    private final AtomicInteger index = new AtomicInteger(0);

    public Server(int port, ExecutorService threadPool) {
        try {
            this.host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            this.host = "26.137.178.91";
        }
        this.port = port;
        this.threadPool = threadPool;

        start();
    }

    public void start() {
        try {
            this.serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Load Balancer rodando em " + host + ":" + port);

            while (running) {
                try {
                    Socket droneSocket = serverSocket.accept();
                    threadPool.submit(() -> handleConnection(droneSocket));
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

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        threadPool.shutdownNow();
    }

    private void handleConnection(Socket loadBalancerSocket) {
        try (Socket socket = loadBalancerSocket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String data = in.readLine();

            if (data == null || data.isBlank()) {
                System.err.println("Dados inválidos recebidos do drone: " + socket.getRemoteSocketAddress());
                return;
            }

            String formattedData = convertToStandardFormat(data);

            // Encaminha os dados para o banco de dados


            // Encaminha os dados para o grupo multicast
            sendMulticastMessage(formattedData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMulticastMessage(String message) {
        try {
            // Socket UDP para comunicação o usuários
            DatagramSocket socketGrupo = new DatagramSocket();
            InetAddress group = InetAddress.getByName(grupo);
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, 50004);
            socketGrupo.send(packet);

            System.out.println("Mensagem multicast enviada: " + message);

            socketGrupo.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String convertToStandardFormat(String rawData) {
        return Optional.ofNullable(rawData)
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .map(s -> s.replaceAll("[(){}\\[\\]]", ""))
                .map(s -> {
                    String delimiter = Stream.of("-", ";", ",", "#")
                            .filter(s::contains)
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Delimitador desconhecido no dado: " + rawData));

                    String[] parts = s.split("\\Q" + delimiter + "\\E");

                    if (parts.length != 4) {
                        throw new IllegalArgumentException("Formato inesperado de dados: " + rawData);
                    }

                    return List.of(
                            parts[2].trim(), // temperatura
                            parts[3].trim(), // umidade
                            parts[0].trim(), // pressao
                            parts[1].trim()  // radiacao
                    );
                })
                .map(list -> list.stream().collect(Collectors.joining("//", "[", "]")))
                .orElseThrow(() -> new IllegalArgumentException("Dados inválidos."));
    }


}
