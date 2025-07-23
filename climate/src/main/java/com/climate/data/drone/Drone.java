package com.climate.data.drone;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.data.config.SharedConfig;
import com.climate.data.utils.DataGeneratorService;
import com.climate.data.utils.RegionFormat;

public class Drone implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Drone.class);
    private static final String BROKER_URL = SharedConfig.MQTT_BROKER_URL;
    private static final Random random = new Random();

    private final String region;
    private final String topic;
    private final RegionFormat regionFormat;
    private MqttClient mqttClient;
    private ScheduledExecutorService scheduler;
    private final DataGeneratorService dataGenerator;

    public Drone(String region) {
        this.region = region;
        this.regionFormat = RegionFormat.fromRegionName(region);
        this.topic = "ufersa/pw/climadata/" + region.toLowerCase();
        this.dataGenerator = new DataGeneratorService(this.regionFormat);
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
                // 1. Coleta e Formata a string de acordo com a região
                String payload = dataGenerator.generateForRegion();
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
