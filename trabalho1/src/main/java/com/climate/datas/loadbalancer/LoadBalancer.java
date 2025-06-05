package com.climate.datas.loadbalancer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.climate.datas.utils.Loggable;
import com.climate.datas.utils.ServerInfo;
import com.climate.datas.utils.common.Communicator;
import com.climate.datas.utils.user.UserResponse;

public class LoadBalancer implements AutoCloseable, Loggable {
    private String host;                            // Endereço do load balancer
    private final int port;                         // Porta do load balancer
    private ServerSocket balancerSocket;            // Socket do load balancer
    private volatile boolean running = false;       // Flag indicadora de execução
    private final ExecutorService threadPool;       // Pool de threads para tratar as conexões
    private final List<ServerInfo> multiCastIp;     // Info dos servidores
    private final AtomicInteger index = new AtomicInteger(0);

    public LoadBalancer() throws IOException {
        this.port = 50000;
        this.host = "10.215.36.129";

        threadPool = Executors.newVirtualThreadPerTaskExecutor();
        multiCastIp = List.of(new ServerInfo("230.0.0.2", 50001), new ServerInfo("230.0.0.3", 50002));
        initialize();
    }

    public void initialize() throws IOException {
        try {
            this.balancerSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
            running = true;
            info("LoadBalancer rodando em " + host + ":" + port);
        } catch (IOException e) {
            erro("Erro ao iniciar o LoadBalancer: " + e.getMessage());
            throw new IOException("Não foi possível iniciar o LoadBalancer na porta " + port + " em " + host, e);
        }
    }

    public void start() throws IllegalStateException {
        try {
            while (running) {
                Socket userSocket = balancerSocket.accept();
                threadPool.execute(() -> handleConnection(userSocket));
            }
        } catch (Exception e) {
            erro("Erro inesperado no LoadBalancer: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void handleConnection(Socket userSocket) {
        try (Communicator user = new Communicator(userSocket, "LoadBalancer")) {
            while (!user.isClosed() && running) {
                UserResponse userResponse = user.receiveJsonMessage(UserResponse.class);

                if (userResponse == null) {
                    info("Usuário desconectado ou enviou mensagem inválida.");
                    break;
                }

                int responseValue = userResponse.getResponse().getValue();

                if (responseValue >= 0 && responseValue <= 1) {
                    sendServer(userResponse, user);
                } else {
                    info("Resposta do usuário inválida: " + responseValue);
                }
            }
        } catch (Exception e) {
            erro("Erro inesperado no LoadBalancer ou Usuário: " + e.getMessage());
        }
    }

    public void sendServer(UserResponse userResponse, Communicator user) {
        switch (userResponse.getResponse().getValue()) {
            case 0 -> { // Hashing
                ServerInfo server = chooseServerConsistentHash(String.valueOf(userResponse.getId()));
                user.sendJsonMessage(server);
                info("Usuário recebeu o grupo de servidores: " + server);
            }
            case 1 -> { // Round-Robin
                ServerInfo server = chooseServerRR();
                user.sendJsonMessage(server);
                info("Usuário recebeu o grupo de servidores: " + server);
            }
        }
    }

    public ServerInfo chooseServerConsistentHash(String userId) {
        int hash = Math.abs(userId.hashCode());
        int serverIndex = hash % multiCastIp.size();
        return multiCastIp.get(serverIndex);
    }

    public ServerInfo chooseServerRR() {
        // Distribuição por Round-Robin
        int i = Math.abs(index.getAndIncrement() % multiCastIp.size());
        return multiCastIp.get(i);
    }

    @Override
    public void close() {
        running = false;
        try {
            if (balancerSocket != null && !balancerSocket.isClosed()) {
                balancerSocket.close();
            }
        } catch (IOException e) {
            erro("Erro ao tentar fechar o socket do LoadBalancer: " + e.getMessage());
        }
        threadPool.shutdownNow();
    }

    public static void main(String[] args) {
        try (ExecutorService executor = Executors.newSingleThreadExecutor(); LoadBalancer loadBalancer = new LoadBalancer()) {
            executor.execute(loadBalancer::start);
            LocalTime inicio = LocalTime.now().withNano(0);
            System.out.println("LoadBalancer iniciado. Aguardando os usuários...");

            // Executa por 3 minutos
            Thread.sleep(180000);

            System.out.println("Encerrando DataCenter...");
            loadBalancer.close();

            LocalTime termino = LocalTime.now().withNano(0);

            Duration duracao = Duration.between(inicio, termino);

            System.out.println("Tempo de execução: " + duracao.getSeconds() + " segundos");

        } catch (Exception e) {
            System.err.println("Erro ao iniciar o LoadBalancer: " + e.getMessage());
        } finally {
            System.out.println("LoadBalancer finalizado.");
        }
    }
}
