package com.climate.datas.drone;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.climate.datas.utils.ServerInfo;
import com.climate.datas.utils.common.Communicator;
import com.climate.datas.utils.drone.DatagramDrone;
import com.climate.datas.utils.drone.DroneId;
import com.climate.datas.utils.drone.RegionFormat;

import lombok.Data;

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

public class Drone extends Communicator implements AutoCloseable {
    private final DroneId droneId;
    private final RegionFormat regionFormat;
    private double pressure;                    // hPa
    private double solarRadiation;              // W/m²
    private double temperature;                 // °C
    private double humidity;                    // %
    private ServerInfo balancerInfo;            // Informações do Load Balancer

    private final ScheduledExecutorService scheduler;
    private final Runnable tarefa;

    private static final Map<String, Supplier<Double>> generators = Map.of("pressure", () -> 950 + Math.random() * 100, "solarRadiation", () -> Math.random() * 1200, "temperature", () -> -30 + Math.random() * 80, "humidity", () -> 20 + Math.random() * 80);

    public Drone(DroneId droneId, RegionFormat regionFormat) {
        super("Drone " + droneId.getValue()); // Define o nome do drone

        this.droneId = droneId;
        this.regionFormat = regionFormat;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.balancerInfo = new ServerInfo("26.137.178.91", 50000);

        try {
            balancerInfo.setHost(InetAddress.getLocalHost().getHostAddress());
            System.out.println(balancerInfo.getHost());
        } catch (UnknownHostException e) {
            System.err.println("Erro ao obter o endereço do host: " + e.getMessage());
        }

        connectBalancer();

        if (isConnected()) {
            // Define a tarefa que será executada periodicamente
            this.tarefa = () -> {
                try {
                    collectData();
                    String data = toString();
                    System.out.println("Drone " + droneId + " collected data: " + data);

                    // Envia os dados para o centro de dados

                } catch (Exception e) {
                    System.err.println("Erro no drone " + droneId + ": " + e.getMessage());
                }
            };
        } else {
            throw new IllegalStateException("O drone " + droneId + " não conseguiu se conectar ao Load Balancer.");
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
                System.out.println("Drone " + droneId.getValue() + " gerou: " + data);

                // Aqui você pode enviar os dados para o centro de dados

                DatagramDrone droneData = new DatagramDrone(droneId.getValue(), data);
                sendJsonMessage(droneData);
            } catch (Exception e) {
                System.err.println("Erro no drone " + droneId + ": " + e.getMessage());
            } finally {
                // Agenda a próxima execução com novo delay
                if (!scheduler.isShutdown()) {
                    scheduleNext();
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
        if (isConnected()) {
            disconnect();
        }
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Drone " + droneId + " não terminou em tempo hábil.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupção durante parada do drone " + droneId);
        }
    }

    private void connectBalancer() {
        connect(balancerInfo.getHost(), balancerInfo.getPort());
    }

    @Override
    public void close() {
        stop();
    }

    private long getRandomDelay() {
        return 2000 + (long) (Math.random() * 3000);
    }

    public void collectData() {
        this.pressure = generators.get("pressure").get();
        this.solarRadiation = generators.get("solarRadiation").get();
        this.temperature = generators.get("temperature").get();
        this.humidity = generators.get("humidity").get();
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
        Drone drone = new Drone(DroneId.NORTE, RegionFormat.NORTE);
        drone.start();

        // Simula a execução por 10 segundos
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        drone.stop();
        System.out.println("Drone " + drone.droneId + " finalizado.");
    }
}
