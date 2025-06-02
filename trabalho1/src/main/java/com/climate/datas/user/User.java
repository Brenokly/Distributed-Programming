package com.climate.datas.user;

import com.climate.datas.utils.Loggable;
import com.climate.datas.utils.ServerInfo;
import com.climate.datas.utils.drone.DatagramDrone;
import com.climate.datas.utils.user.UserResponseEnum;
import com.climate.datas.utils.common.Communicator;
import com.climate.datas.utils.user.UserResponse;

import java.net.*;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Stream;

public class User extends Communicator implements Loggable, AutoCloseable {
    private final int id;                           // ID do usuário
    private final int portBalancer;                 // Porta do load balancer
    private String hostBalancer;                    // host do load balancer
    private InetSocketAddress grupo;                // Endereço do grupo multicast
    private MulticastSocket dataSocket;             // MulticastSocket
    private NetworkInterface interfaceAddress;      // Endereço da interface de rede
    private ServerInfo servidor;                    // Informações do servidor
    private final Scanner scanner;                  // Scanner para entrada do usuário
    private volatile boolean running = false;       // Flag indicadora de execução
    private final String interfaceName = "Ethernet"; // Nome da interface de rede

    private static final Map<Integer, UserResponseEnum> OPTIONS = Map.of(0, UserResponseEnum.HASHING, 1, UserResponseEnum.ROUND_ROBIN);

    public User(int id) {
        super("User-" + id);
        this.id = id;
        this.portBalancer = 50000; // Porta do load balancer
        try {
            this.hostBalancer = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            this.hostBalancer = "26.137.178.91";
        }
        scanner = new Scanner(System.in);
        try {
            interfaceAddress = NetworkInterface.getByName(interfaceName);
        } catch (SocketException e) {
            erro("Erro ao obter a interface de rede: " + e.getMessage());
        }
        getServerInfo();
    }

    public void getServerInfo() {
        connect(hostBalancer, portBalancer);
        boolean received = false;

        if (!isConnected()) {
            erro("Não foi possível conectar ao Load Balancer em " + hostBalancer + ":" + portBalancer);
            return;
        }

        while (isConnected() && !received) {
            System.out.println("Escolha a técnica de balanceamento de carga!");
            System.out.println("0. Hashing (Consistent Hashing)");
            System.out.println("1. Round Robin");
            System.out.print("Opção: ");
            int choice = scanner.nextInt();

            Optional.ofNullable(OPTIONS.get(choice)).ifPresentOrElse(responseEnum -> sendJsonMessage(new UserResponse(id, responseEnum)), () -> System.out.println("Opção inválida. Tente novamente."));

            Optional.ofNullable(receiveJsonMessage(ServerInfo.class)).ifPresentOrElse(s -> {
                servidor = s;
                info("Grupo de Servidor Recebido: " + servidor.getHost() + ":" + servidor.getPort());
            }, () -> erro("Nenhum grupo multicast disponível no LoadBalancer."));

            received = true;
        }
        disconnect();
        connectGroup();
    }

    public void connectGroup() {
        try {
            dataSocket = new MulticastSocket(servidor.getPort());
            grupo = new InetSocketAddress(InetAddress.getByName(servidor.getHost()), servidor.getPort());
            dataSocket.joinGroup(grupo, interfaceAddress);
            running = true;
            info("Usuário " + id + " conectado ao grupo multicast: " + servidor.getHost() + ":" + servidor.getPort());
        } catch (Exception e) {
            erro("Erro ao conectar ao grupo multicast: " + e.getMessage());
        }
    }

    public void start() {
        if (!running) {
            info("Usuário " + id + " não está conectado ao grupo multicast.");
        }
        try {
            info("Usuário " + id + " aguardando mensagens do grupo multicast...");

            /*
             * Testando esse Stream.Generate para receber pacotes de forma assíncrona.
             * A ideia é que ele continue recebendo pacotes até que a flag 'running' seja false.
             *
             * O generate cria um fluxo infinito. Recebe uma função que gera uma saída a cada chamada.
             * Nesse caso, ele gera um DatagramPacket a cada chamada, recebendo pacotes do MulticastSocket.
             * Quando o pacote é recebido, ele é processado, validado e impresso.
            */
            Stream.generate(() -> {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    dataSocket.receive(packet);
                    return packet;
                } catch (Exception e) {
                    erro("Erro ao receber pacote: " + e.getMessage());
                    running = false;
                    return null;
                }
            }).takeWhile(packet -> running && packet != null).forEach(packet -> {
                info("Mensagem recebida do grupo multicast: " + packet.getAddress() + ":" + packet.getPort());
                printMessage(DatagramDrone.fromPacket(packet));
            });
        } catch (Exception e) {
            erro("Erro inesperado no LoadBalancer: " + e.getMessage());
        } finally {
            close();
        }
    }

    public void printMessage(DatagramDrone message) {
        info("Mensagem recebida do grupo multicast: " + message);
    }

    @Override
    public void close() {
        running = false;
        if (scanner != null) {
            scanner.close();
        }
        disconnect();
        Optional.ofNullable(dataSocket).filter(s -> !s.isClosed() && grupo != null && interfaceAddress != null).ifPresent(socket -> {
            try {
                socket.leaveGroup(grupo, interfaceAddress);
                socket.close();
            } catch (Exception e) {
                erro("Erro ao fechar o MulticastSocket: " + e.getMessage());
            }
        });
    }
}
