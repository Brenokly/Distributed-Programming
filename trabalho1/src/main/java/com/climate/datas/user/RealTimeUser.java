// package com.climate.datas.user; // Mantenha seu pacote
package com.climate.datas.user;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealTimeUser implements Runnable, MqttCallback, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeUser.class);
    private static final String MQTT_BROKER = "tcp://mqtt.eclipseprojects.io:1883";
    private static final String BASE_TOPIC = "ufersa/pw/gateway/processed_data";

    private MqttClient mqttClient;

    public RealTimeUser() throws MqttException {
        String clientId = "realtime-user-" + System.currentTimeMillis();
        mqttClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());
        mqttClient.setCallback(this);
    }

    @Override
    public void run() {
        try {
            logger.info("RealTime-User conectando ao broker MQTT...");
            mqttClient.connect();
            logger.info("RealTime-User conectado.");

            // Menu interativo para o usuário escolher o tópico
            handleSubscriptionChoice();

        } catch (MqttException e) {
            logger.error("Erro ao iniciar o RealTime-User: {}", e.getMessage());
        }
    }

    private void handleSubscriptionChoice() {
        Scanner scanner = new Scanner(System.in);
        while (mqttClient.isConnected()) {
            System.out.println("\nEscolha a região para monitorar em tempo real:");
            System.out.println("1: Todas as Regiões");
            System.out.println("2: Norte");
            System.out.println("3: Sul");
            System.out.println("4: Leste");
            System.out.println("5: Oeste");
            System.out.println("0: Sair");
            System.out.print("Opção: ");
            int choice = scanner.nextInt();

            String topicFilter = "";
            switch (choice) {
                case 1 ->
                    topicFilter = BASE_TOPIC + "/#"; // Receber todos os dados [cite: 949]
                case 2 ->
                    topicFilter = BASE_TOPIC + "/norte"; // Receber dados de uma região específica [cite: 950]
                case 3 ->
                    topicFilter = BASE_TOPIC + "/sul";
                case 4 ->
                    topicFilter = BASE_TOPIC + "/leste";
                case 5 ->
                    topicFilter = BASE_TOPIC + "/oeste";
                case 0 -> {
                    return; // Sai do loop e permite o encerramento
                }
                default -> {
                    System.out.println("Opção inválida. Tente novamente.");
                    continue;
                }
            }
            try {
                logger.info("Inscrevendo-se no tópico: {}", topicFilter);
                mqttClient.subscribe(topicFilter, 1);
                System.out.println("Monitorando... Pressione Ctrl+C e reinicie para trocar de tópico.");
                // Mantém o thread principal em espera
                while (true) {
                    Thread.sleep(10000);
                }
            } catch (InterruptedException | MqttException e) {
                logger.error("Falha na inscrição do tópico: {}", e.getMessage());
            }
        }
        scanner.close();
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.warn("Conexão MQTT perdida: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        String region = topic.substring(topic.lastIndexOf('/') + 1);
        System.out.printf("[TEMPO REAL | %s] -> %s%n", region.toUpperCase(), payload);
    }

    @Override
    public void close() throws Exception {
        if (mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        logger.info("RealTime-User desconectado.");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Não utilizado, mas necessário para a interface MqttCallback
    }
}
