package com.climate.datas.server;

import static com.climate.datas.utils.DataConverter.convertToStandardFormat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.climate.datas.database.DataBase;
import com.climate.datas.utils.Loggable;
import com.climate.datas.utils.common.Communicator;
import com.climate.datas.utils.drone.DatagramDrone;

public class Server implements AutoCloseable, Loggable {
    private final String name;                      // Nome do servidor
    private String host;                            // Endereço do servidor
    private final int port;                         // Porta do servidor
    private final String ipMulticast;               // Endereço IP multicast para envio de dados
    private ServerSocket serverSocket;              // Socket do server atual
    private volatile boolean running = false;       // Flag indicadora de execução
    private final ExecutorService threadPool;       // Pool de threads para tratar as conexões
    private final DataBase database;                // Referência ao banco de dados

    public Server(int port, String ipMulticast, DataBase database) throws IOException {
        this.port = port;
        this.ipMulticast = ipMulticast;
        this.host = "10.10.71.85";
        this.name = "Server-" + port; // Nome do servidor baseado na porta
        this.threadPool = Executors.newVirtualThreadPerTaskExecutor();
        this.database = database;
        initialize();
    }

    public void initialize() throws IOException {
        try {
            this.serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
            running = true;
            info(name + " rodando em " + host + ":" + port);
        } catch (IOException e) {
            erro("Erro ao iniciar o Servidor: " + e.getMessage());
            throw new IOException("Não foi possível iniciar o Servidor na porta " + port + " em " + host, e);
        }
    }

    public void start() throws IllegalStateException {
        if (!running) {
            throw new IllegalStateException("Servidor não foi inicializado. Chame initialize() primeiro.");
        }
        try {
            while (running) {
                Socket loaderSocket = serverSocket.accept();
                threadPool.execute(() -> handleConnection(loaderSocket));
            }
        } catch (IOException e) {
            info("Conexão encerrada ou erro inesperado no " + name + ": " + e.getMessage());
        } catch (Exception e) {
            erro("Erro inesperado no Servidor: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void handleConnection(Socket DataCenter) {
        try (Communicator communicatorSocket = new Communicator(DataCenter, name)) {
            while (communicatorSocket.isConnected() && running) {
                DatagramDrone data = communicatorSocket.receiveJsonMessage(DatagramDrone.class);

                if (data == null) {
                    erro("Dados recebidos são nulos — conexão provavelmente encerrada");
                    break;
                }

                data.setData(convertToStandardFormat(data.getData()));

                // Encaminha os dados para o banco de dados
                database.saveData(data.getDroneId().getValue(), data.getData());

                info(name + " Mensagem recebida e salva no banco de dados: " + data.getData());

                // Encaminha os dados para o grupo multicast
                sendMulticastMessage(data);
            }
        } catch (Exception e) {
            erro("DataCenter desconectado ou erro ao processar dados: " + e.getMessage());
        }
    }

    private void sendMulticastMessage(DatagramDrone message) {
        try {
            // Socket UDP para comunicação o usuários
            DatagramSocket socketGrupo = new DatagramSocket();
            InetAddress group = InetAddress.getByName(ipMulticast);
            byte[] buffer = message.toBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
            socketGrupo.send(packet);

            info("Mensagem multicast enviada para o grupo " + ipMulticast);

            socketGrupo.close();
        } catch (IOException e) {
            erro("Erro ao enviar mensagem para o grupo MultiCast: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            erro("Erro ao tentar fechar o socket do " + name + ": " + e.getMessage());
        }
        threadPool.shutdownNow();
    }

    public static void main(String[] args) {
        DataBase dataBase = new DataBase();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

        try (
                executor;
                Server server1 = new Server(50001, "230.0.0.2", dataBase);
                Server server2 = new Server(50002, "230.0.0.3", dataBase)
        ) {
            // Dando start nos servidores
            executor.execute(server1::start);
            executor.execute(server2::start);

            System.out.println("Servidores iniciados...");

            executor.schedule(() -> {
                System.out.println("Encerrando servidores...");
                server2.close();
                server1.close();

            }, 180, TimeUnit.SECONDS);

            LocalTime inicio = LocalTime.now().withNano(0);

            executor.shutdown();
            if (!executor.awaitTermination(180, TimeUnit.SECONDS)) {
                System.out.println("Forçando encerramento...");
                executor.shutdownNow();
            }

            LocalTime termino = LocalTime.now().withNano(0);

            Duration duracao = Duration.between(inicio, termino);

            System.out.println("Tempo de execução: " + duracao.getSeconds() + " segundos");
        } catch (Exception e) {
            System.err.println("Erro ao iniciar os servidores: " + e.getMessage());
        } finally {
            System.out.println("Servidores finalizado.");
        }

        // Imprime todos os dados armazenados no banco de dados
        dataBase.printAllData();
    }
}
