package com.climate.datas.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.database.DataBase;
import com.climate.datas.utils.ClimateData;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class Gateway implements MqttCallback, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Gateway.class);

    // Conexões MQTT
    private static final String MQTT_BROKER = "tcp://broker.emqx.io:1883";
    private static final String DRONE_TOPIC_FILTER = "ufersa/pw/climadata/#";
    private static final String GATEWAY_PUBLISH_TOPIC = "ufersa/pw/gateway/processed_data";
    private MqttClient mqttClient;

    // Conexões RabbitMQ
    private static final String RABBITMQ_HOST = "localhost";
    private static final String RABBITMQ_EXCHANGE_NAME = "climate_data_topic_exchange";
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    private final DataBase inMemoryDatabase = new DataBase();
    private final ExecutorService processingExecutor = Executors.newCachedThreadPool();

    public Gateway() {
    }

    //Inicia as conexões com os brokers MQTT e RabbitMQ.
    public void start() throws IOException, TimeoutException, MqttException {
        // Configuração do RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        this.rabbitConnection = factory.newConnection();
        this.rabbitChannel = rabbitConnection.createChannel();

        rabbitChannel.exchangeDeclare(RABBITMQ_EXCHANGE_NAME, "topic");
        logger.info("Gateway conectado ao RabbitMQ e exchange de TÓPICO '{}' declarada.", RABBITMQ_EXCHANGE_NAME);

        // Configuração do cliente MQTT
        String clientId = "gateway-" + System.currentTimeMillis();
        mqttClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(false);
        connOpts.setAutomaticReconnect(true);

        mqttClient.setCallback(this);
        logger.info("Gateway conectando ao broker MQTT...");
        mqttClient.connect(connOpts);
        logger.info("Gateway conectado.");
        mqttClient.subscribe(DRONE_TOPIC_FILTER, 1);
        logger.info("Gateway inscrito no tópico de drones: {}", DRONE_TOPIC_FILTER);
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.warn("Conexão com o broker MQTT perdida! Causa: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        processingExecutor.submit(() -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                String region = topic.substring(topic.lastIndexOf('/') + 1);
                ClimateData data = parseDroneData(region, payload);
                if (data == null) {
                    return;
                }

                System.out.println("Dados recebidos do drone " + region + ": " + data);

                inMemoryDatabase.saveData(data.region(), data.toString());
                logger.info("Gateway armazenou dados de {}: {}", region, data);

                publishToRabbitMQ(data); // Publica para o Dashboard
                publishToMqtt(data);     // Publica para o RealTime

            } catch (Exception e) {
                logger.error("Erro ao processar mensagem de {}: {}", topic, e.getMessage());
                System.out.println("Erro ao processar mensagem de " + topic + ": " + e.getMessage());
            }
        });
    }

    private ClimateData parseDroneData(String region, String payload) {
        String[] stringValues;

        switch (region.toUpperCase()) {
            case "NORTE" -> // Divide a string usando o hífen como delimitador
                stringValues = payload.split("-");
            case "SUL" ->  // Remove os parênteses e depois divide pelo ponto e vírgula
                stringValues = payload.replace("(", "").replace(")", "").split(";");
            case "LESTE" -> // Remove as chaves e depois divide pela vírgula
                stringValues = payload.replace("{", "").replace("}", "").split(",");
            case "OESTE" -> // Divide a string usando a cerquilha como delimitador
                stringValues = payload.split("#");
            default -> {
                logger.warn("Região desconhecida para parsing: {}", region);
                return null;
            }
        }

        if (stringValues.length != 4) {
            logger.warn("Payload da região {} não contém 4 valores: {}", region, payload);
            return null;
        }

        try {
            double pressao = Double.parseDouble(stringValues[0]);
            double radiacao = Double.parseDouble(stringValues[1]);
            double temperatura = Double.parseDouble(stringValues[2]);
            double umidade = Double.parseDouble(stringValues[3]);

            return new ClimateData(region, temperatura, umidade, pressao, radiacao, LocalDateTime.now());

        } catch (NumberFormatException e) {
            logger.error("Erro de formato de número ao parsear payload da região {}: {}", region, payload, e);
            return null;
        }
    }

    private synchronized void publishToRabbitMQ(ClimateData data) {
        try {
            String message = data.toString();
            String routingKey = "dados.climaticos." + data.region().toLowerCase();

            rabbitChannel.basicPublish(RABBITMQ_EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
            logger.info("Gateway publicou para Exchange RabbitMQ '{}' com chave '{}'", RABBITMQ_EXCHANGE_NAME, routingKey);
        } catch (IOException e) {
            logger.error("Falha CRÍTICA ao publicar no RabbitMQ: {}", e.getMessage());
        }
    }

    private void publishToMqtt(ClimateData data) {
        try {
            String message = data.toString();
            MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);
            mqttClient.publish(GATEWAY_PUBLISH_TOPIC + "/" + data.region(), mqttMessage);
            logger.info("Gateway publicou para Tópico MQTT '{}': {}", GATEWAY_PUBLISH_TOPIC + "/" + data.region(), message);
        } catch (MqttException e) {
            logger.error("Falha ao publicar no MQTT: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        processingExecutor.shutdownNow();
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        if (rabbitChannel != null && rabbitChannel.isOpen()) {
            rabbitChannel.close();
        }
        if (rabbitConnection != null && rabbitConnection.isOpen()) {
            rabbitConnection.close();
        }
        logger.info("Gateway finalizado.");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Método necessário, mas não utilizado!
    }

    public static void main(String[] args) {
        try {
            Gateway gateway = new Gateway();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    gateway.close();
                } catch (Exception e) {
                    logger.error("Erro ao fechar o gateway.", e);
                }
            }));

            // Inicia o gateway
            gateway.start();
            logger.info("Gateway está em execução. Pressione Ctrl+C para encerrar.");

            // Mantém a aplicação rodando
            Thread.currentThread().join();

        } catch (IOException | InterruptedException | TimeoutException | MqttException e) {
            logger.error("Falha fatal ao iniciar o Gateway: {}", e.getMessage(), e);
        }
    }
}
