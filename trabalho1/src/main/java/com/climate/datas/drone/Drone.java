package com.climate.datas.drone;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import com.climate.datas.utils.Loggable;
import com.climate.datas.utils.ServerInfo;
import com.climate.datas.utils.drone.*;

/*
 * Drones são coletores de dados que sobrevoam regiões (Norte, Sul, Leste, Oeste)
 * e coletam dados de pressão atmosférica, radiação solar, temperatura e umidade.
 * Teremos 4 drones, um para cada região. Cada drone coleta dados no seguinte formato:
 *
 * ○ Drone norte coleta os dados no formato: pressao-radiacao-temperatura-umidade.
 * ○ Drone sul coleta os dados no formato: (pressao;radiacao;temperatura;umidade).
 * ○ Drone leste coleta os dados no formato: {pressao,radiacao,temperatura,umidade}.
 * ○ Drone oeste coleta os dados no formato: pressao#radiacao#temperatura#umidade.
 *
 * Os dados coletados pelos drones são enviados para um centro de dados.
 *
 * A interface AutoCloseable é implementada para garantir que os recursos sejam liberados corretamente.
 * Os drones utilizam um ScheduledExecutorService para agendar a coleta de dados periodicamente.
 * Cada drone coleta dados a cada 2 a 5 segundos, com um delay aleatório entre as coletas.
 */
public class Drone implements AutoCloseable, Loggable {

    private final DroneId droneId;              // Identificador do drone (NORTE, SUL, LESTE, OESTE)
    private final RegionFormat regionFormat;    // Formato de região do drone (NORTE, SUL, LESTE, OESTE)
    private double pressure;                    // hPa
    private double solarRadiation;              // W/m²
    private double temperature;                 // °C
    private double humidity;                    // %
    private final ServerInfo datacenter;        // Informações do Load Balancer
    private final DatagramSocket droneSocket;   // Socket do drone para comunicação com o Data Center
    private static final Generators g = new Generators();

    private final ScheduledExecutorService scheduler;

    private static final Map<String, BiFunction<Double, Double, Double>> generators = Map.of(
            "pressure", g::generate,
            "solarRadiation", g::generate,
            "temperature", g::generate,
            "humidity", g::generate
    );

    private static final Map<DroneId, Map<String, Range>> rangesByRegion = Map.of(
            DroneId.NORTE, Map.of(
                    "pressure", new Range(950, 1000),
                    "solarRadiation", new Range(800, 1200),
                    "temperature", new Range(30, 40),
                    "humidity", new Range(70, 90)
            ),
            DroneId.SUL, Map.of(
                    "pressure", new Range(1000, 1050),
                    "solarRadiation", new Range(400, 800),
                    "temperature", new Range(10, 20),
                    "humidity", new Range(60, 80)
            ),
            DroneId.LESTE, Map.of(
                    "pressure", new Range(970, 1030),
                    "solarRadiation", new Range(600, 1000),
                    "temperature", new Range(25, 35),
                    "humidity", new Range(50, 70)
            ),
            DroneId.OESTE, Map.of(
                    "pressure", new Range(980, 1020),
                    "solarRadiation", new Range(500, 900),
                    "temperature", new Range(20, 30),
                    "humidity", new Range(55, 75)
            )
    );

    public Drone(DroneId droneId) {
        this.droneId = droneId;
        this.regionFormat = RegionFormat.fromDroneId(droneId);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.datacenter = new ServerInfo("230.0.0.1", 49999);
        try {
            droneSocket = new DatagramSocket();
        } catch (IOException e) {
            erro("Não foi possível inicializar o socket do drone " + droneId.getValue() + ": " + e.getMessage());
            throw new RuntimeException("Erro ao inicializar o drone", e);
        }
    }

    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        long delay = getRandomDelay();

        scheduler.schedule(() -> {
            try {
                collectData();
                String data = toString();
                infoNoLine("Drone " + droneId.getValue() + " gerou: " + data);

                // Enviando para o centro de dados
                sendMessageDataCenter(new DatagramDrone(droneId, data));
            } catch (Exception e) {
                erro("Houve um erro ao enviar dados do drone " + droneId.getValue() + ": " + e.getMessage());
            } finally {
                // Agenda a próxima execução com novo delay
                if (!scheduler.isShutdown()) {
                    scheduleNext();
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void sendMessageDataCenter(DatagramDrone message) {
        if (message == null) {
            erro("Mensagem nula não pode ser enviada.");
            return;
        }

        byte[] buffer = message.toBytes();
        if (buffer.length == 0) {
            erro("Mensagem vazia não pode ser enviada.");
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(datacenter.getHost());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, datacenter.getPort());
            droneSocket.send(packet);
            info("Mensagem enviada com sucesso para o IP Multicast: " + datacenter.getHost() + ":" + datacenter.getPort());
        } catch (IOException e) {
            erro("Falha ao enviar mensagem para o balanceador: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (droneSocket != null && !droneSocket.isClosed()) {
            droneSocket.close();
        }
        scheduler.shutdownNow();
    }

    private long getRandomDelay() {
        return 2000 + (long) (Math.random() * 3000);
    }

    public void collectData() {
        Map<String, Range> ranges = rangesByRegion.get(this.droneId);

        this.pressure = generateValue("pressure", ranges);
        this.solarRadiation = generateValue("solarRadiation", ranges);
        this.temperature = generateValue("temperature", ranges);
        this.humidity = generateValue("humidity", ranges);
    }

    private double generateValue(String key, Map<String, Range> ranges) {
        Range range = ranges.get(key);
        return generators.get(key).apply(range.min(), range.max());
    }

    @Override
    public String toString() {
        String[] values = Stream.of(pressure, solarRadiation, temperature, humidity).map(String::valueOf).toArray(String[]::new);

        if (values.length != 4) {
            return "Invalid data";
        }

        return regionFormat.format(values);
    }

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        int option = -1;
        while (option != 0) {
            System.out.println("Selecione uma opção:");
            System.out.println("1 - Iniciar simulação de drones");
            System.out.println("0 - Sair");
            System.out.print("Opção: ");

            try {
                option = scanner.nextInt();
                if (option != 0 && option != 1) {
                    System.out.println("Opção inválida. Por favor, selecione uma opção válida.");
                    continue;
                } else if (option == 0) {
                    System.out.println("Saindo...");
                    break;
                } else {
                    System.out.println("Iniciando simulação de drones...");
                }
            } catch (Exception e) {
                System.out.println("Opção inválida. Por favor, insira um número.");
                scanner.next();
                continue;
            }

            try (
                    ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
                    Drone drone1 = new Drone(DroneId.NORTE);
                    Drone drone2 = new Drone(DroneId.SUL);
                    Drone drone3 = new Drone(DroneId.LESTE);
                    Drone drone4 = new Drone(DroneId.OESTE)
            ) {
                executor.execute(drone1::start);
                executor.execute(drone2::start);
                executor.execute(drone3::start);
                executor.execute(drone4::start);

                System.out.println("Simulação iniciada.");

                executor.schedule(() -> {
                    System.out.println("Encerrando drones...");
                    drone1.close();
                    drone2.close();
                    drone3.close();
                    drone4.close();

                    System.out.println("Coleta e Envio de dados finalizados.");
                }, 10, TimeUnit.SECONDS);

                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("Forçando encerramento...");
                    executor.shutdownNow();
                }

            } catch (Exception e) {
                System.out.println("Houve problema com os drones: " + e.getMessage());
            }
        }
    }
}
