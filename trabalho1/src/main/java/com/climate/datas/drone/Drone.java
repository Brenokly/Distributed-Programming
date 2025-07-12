package com.climate.datas.drone;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.utils.drone.Range;
import com.climate.datas.utils.drone.RegionFormat;

public class Drone implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Drone.class);
    private static final String BROKER_URL = "tcp://broker.emqx.io:1883";               // Usei um broker diferente!
    private static final Random random = new Random();

    private final String region;
    private final String topic;
    private final RegionFormat regionFormat;
    private MqttClient mqttClient;
    private ScheduledExecutorService scheduler;

    private static final Map<String, BiFunction<Double, Double, Double>> generators = Map.of(
            "pressure", (min, max) -> min + (max - min) * random.nextDouble(),
            "solarRadiation", (min, max) -> min + (max - min) * random.nextDouble(),
            "temperature", (min, max) -> min + (max - min) * random.nextDouble(),
            "humidity", (min, max) -> min + (max - min) * random.nextDouble()
    );
    private static final Map<String, Map<String, Range>> rangesByRegion = Map.of(
            "NORTE", Map.of(
                    "pressure", new Range(950, 1000), "solarRadiation", new Range(800, 1200),
                    "temperature", new Range(30, 40), "humidity", new Range(70, 90)),
            "SUL", Map.of(
                    "pressure", new Range(1000, 1050), "solarRadiation", new Range(400, 800),
                    "temperature", new Range(10, 20), "humidity", new Range(60, 80)),
            "LESTE", Map.of(
                    "pressure", new Range(970, 1030), "solarRadiation", new Range(600, 1000),
                    "temperature", new Range(25, 35), "humidity", new Range(50, 70)),
            "OESTE", Map.of(
                    "pressure", new Range(980, 1020), "solarRadiation", new Range(500, 900),
                    "temperature", new Range(20, 30), "humidity", new Range(55, 75))
    );

    public Drone(String region) {
        this.region = region;
        this.regionFormat = RegionFormat.fromRegionName(region);
        this.topic = "ufersa/pw/climadata/" + region.toLowerCase();
    }

    public void connect() throws MqttException {
        String clientId = "drone-" + region + "-" + System.currentTimeMillis();
        mqttClient = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setAutomaticReconnect(true);

        logger.info("Drone {} conectando ao broker: {}", region, BROKER_URL);
        mqttClient.connect(connOpts);
        logger.info("Drone {} conectado com sucesso.", region);
    }

    public void startCollecting() {
        if (mqttClient == null || !mqttClient.isConnected()) {
            logger.error("Não é possível iniciar a coleta. Drone não está conectado ao MQTT.");
            return;
        }
        System.out.println("Iniciando coleta de dados para a região " + region + "...");
        logger.info("Iniciando a coleta de dados para a região {}.", region);
        if (scheduler == null || scheduler.isShutdown()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        scheduleNext();
    }

    private void scheduleNext() {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        long delay = 2000 + random.nextInt(3001);

        scheduler.schedule(() -> {
            try {
                // 1. Coleta os dados
                Map<String, Range> ranges = rangesByRegion.get(this.region.toUpperCase());
                double pressure = generateValue("pressure", ranges);
                double solarRadiation = generateValue("solarRadiation", ranges);
                double temperature = generateValue("temperature", ranges);
                double humidity = generateValue("humidity", ranges);

                // 2. Formata a string de acordo com a região
                String payload = formatData(pressure, solarRadiation, temperature, humidity);
                logger.info("Drone {} gerou: {}", region, payload);

                // 3. Publica via MQTT
                if (mqttClient.isConnected()) {
                    MqttMessage message = new MqttMessage(payload.getBytes());
                    message.setQos(1);
                    mqttClient.publish(topic, message);
                    logger.debug("Drone {} publicou no tópico '{}'", region, topic);
                    System.out.println("\nDrone " + region + " publicou: " + payload);
                } else {
                    logger.warn("Drone {} não está conectado, pulando publicação.", region);
                }

            } catch (MqttException e) {
                logger.error("Erro na tarefa do drone {}: {}", region, e.getMessage());
            } finally {
                // Reagenda a próxima execução
                scheduleNext();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private String formatData(double pressure, double solarRadiation, double temperature, double humidity) {
        String[] values = Stream.of(pressure, solarRadiation, temperature, humidity)
                .map(d -> String.format(Locale.US, "%.2f", d)) // Formata com 2 casas decimais e ponto
                .toArray(String[]::new);

        return switch (this.regionFormat) {
            case NORTE ->
                String.join("-", values);
            case SUL ->
                String.format("(%s;%s;%s;%s)", (Object[]) values);
            case LESTE ->
                String.format("{%s,%s,%s,%s}", (Object[]) values);
            case OESTE ->
                String.join("#", values);
        };
    }

    private double generateValue(String key, Map<String, Range> ranges) {
        Range range = ranges.get(key);
        return generators.get(key).apply(range.min(), range.max());
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                logger.info("Drone {} desconectado.", region);
            }
        } catch (MqttException e) {
            logger.error("Erro ao desconectar drone {}: {}", region, e.getMessage());
        }
    }

    public void simulateMqttFailure() {
        logger.warn("!!! SIMULANDO FALHA DE COMUNICAÇÃO - PARANDO AGENDADOR E DESCONECTANDO FORÇADAMENTE !!!");

        // 1. Para imediatamente a geração de novas mensagens
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            logger.info("Agendador de tarefas do drone parado.");
        }

        // 2. Desconecta à força para simular uma queda de rede
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                // Este método (em teoria) corta a conexão abruptamente
                mqttClient.disconnectForcibly();
                logger.info("Conexão MQTT forçadamente encerrada.");
            }
        } catch (MqttException e) {
            logger.error("Erro ao forçar a desconexão para simular falha.", e);
        }
    }
}
