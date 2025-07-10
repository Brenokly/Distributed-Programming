// package com.climate.datas.gateway; // Mantenha seu pacote
package com.climate.datas.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
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

public class Gateway implements Runnable, MqttCallback, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Gateway.class);

    // Conexões MQTT
    private static final String MQTT_BROKER = "tcp://mqtt.eclipseprojects.io:1883";
    private static final String DRONE_TOPIC_FILTER = "ufersa/pw/climadata/#"; // Filtro para todos os drones
    private static final String GATEWAY_PUBLISH_TOPIC = "ufersa/pw/gateway/processed_data";
    private MqttClient mqttClient;

    // Conexões RabbitMQ
    private static final String RABBITMQ_HOST = "localhost";
    private static final String RABBITMQ_QUEUE_NAME = "climate_dashboard_queue";
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    private final DataBase inMemoryDatabase = new DataBase();
    private final ExecutorService processingExecutor = Executors.newCachedThreadPool();

    public Gateway() throws MqttException, IOException, TimeoutException {
        // Configuração do cliente MQTT
        String clientId = "gateway-" + System.currentTimeMillis();
        mqttClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());
        mqttClient.setCallback(this);

        // Configuração do RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        this.rabbitConnection = factory.newConnection();
        this.rabbitChannel = rabbitConnection.createChannel();
        // Declara uma fila durável
        rabbitChannel.queueDeclare(RABBITMQ_QUEUE_NAME, true, false, false, null);
    }

    @Override
    public void run() {
        try {
            logger.info("Gateway conectando ao broker MQTT...");
            mqttClient.connect();
            logger.info("Gateway conectado.");
            // Assina o tópico dos drones
            mqttClient.subscribe(DRONE_TOPIC_FILTER, 1);
            logger.info("Gateway inscrito no tópico: {}", DRONE_TOPIC_FILTER);
        } catch (MqttException e) {
            logger.error("Erro ao iniciar o Gateway: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.warn("Conexão com o broker MQTT perdida! Causa: {}", cause.getMessage());
        // Aqui poderia ser implementada a lógica de reconexão
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // Submete o processamento da mensagem para outro thread para não bloquear o callback
        processingExecutor.submit(() -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            logger.debug("Mensagem recebida do tópico {}: {}", topic, payload);

            try {
                // Extrai a região do tópico
                String region = topic.substring(topic.lastIndexOf('/') + 1);

                // Parseia a mensagem e converte para o formato padrão
                ClimateData data = parseDroneData(region, payload);
                if (data == null) {
                    logger.warn("Não foi possível parsear os dados da região {}: {}", region, payload);
                    return;
                }

                // 1. Armazena na base de dados em memória
                inMemoryDatabase.saveData(data.region(), data.toString());
                logger.info("Gateway armazenou dados de {}: {}", region, data);

                // 2. Publica no RabbitMQ para o dashboard/histórico [cite: 1170]
                publishToRabbitMQ(data);

                // 3. Publica no MQTT para consumidores em tempo real [cite: 1178]
                publishToMqtt(data);

            } catch (Exception e) {
                logger.error("Erro ao processar mensagem de {}: {}", topic, e.getMessage());
            }
        });
    }

    private ClimateData parseDroneData(String region, String payload) {
        // Regex para extrair 4 números de uma string, ignorando os caracteres especiais
        Pattern pattern = Pattern.compile("[\\d\\.\\-]+");
        Matcher matcher = pattern.matcher(payload);

        double[] values = new double[4];
        int i = 0;
        while (matcher.find() && i < 4) {
            values[i++] = Double.parseDouble(matcher.group());
        }

        if (i != 4) {
            return null; // Não encontrou 4 valores
        }
        return new ClimateData(
                region,
                values[2], // temperatura
                values[3], // umidade
                values[0], // pressao
                values[1], // radiacao
                LocalDateTime.now()
        );
    }

    private void publishToRabbitMQ(ClimateData data) {
        try {
            String message = data.toString(); // Usando o formato padrão: [temperatura | umidade | pressao | radiacao]
            rabbitChannel.basicPublish("", RABBITMQ_QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            logger.info("Gateway publicou para RabbitMQ: {}", message);
        } catch (IOException e) {
            logger.error("Falha ao publicar no RabbitMQ: {}", e.getMessage());
        }
    }

    private void publishToMqtt(ClimateData data) {
        try {
            String message = data.region() + "|" + data.toString();
            MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);
            // Publica em um tópico específico para dados processados
            mqttClient.publish(GATEWAY_PUBLISH_TOPIC + "/" + data.region(), mqttMessage);
            logger.info("Gateway publicou para MQTT em tempo real: {}", message);
        } catch (MqttException e) {
            logger.error("Falha ao publicar no MQTT: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        processingExecutor.shutdownNow();
        if (mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        if (rabbitChannel.isOpen()) {
            rabbitChannel.close();
        }
        if (rabbitConnection.isOpen()) {
            rabbitConnection.close();
        }
        logger.info("Gateway finalizado.");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Método necessário, mas não utilizado!
    }
}
