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
    private static final String MQTT_BROKER = "tcp://broker.emqx.io:1883";
    private static final String DRONE_TOPIC_FILTER = "ufersa/pw/climadata/#";
    private static final String GATEWAY_PUBLISH_TOPIC = "ufersa/pw/gateway/processed_data";
    private MqttClient mqttClient;

    // Conexões RabbitMQ
    private static final String RABBITMQ_HOST = "localhost";
    private static final String RABBITMQ_EXCHANGE_NAME = "climate_data_exchange";
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    private final DataBase inMemoryDatabase = new DataBase();
    private final ExecutorService processingExecutor = Executors.newCachedThreadPool();

    public void start() throws IOException, TimeoutException, MqttException {
        // Configuração do RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        this.rabbitConnection = factory.newConnection();
        this.rabbitChannel = rabbitConnection.createChannel();
        // MUDANÇA: Declara uma exchange do tipo fanout. Ela irá distribuir as msgs para todos os consumidores.
        rabbitChannel.exchangeDeclare(RABBITMQ_EXCHANGE_NAME, "fanout");
        logger.info("Gateway conectado ao RabbitMQ e exchange '{}' declarada.", RABBITMQ_EXCHANGE_NAME);

        // Configuração do cliente MQTT
        String clientId = "gateway-" + System.currentTimeMillis();
        mqttClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());
        mqttClient.setCallback(this);
        logger.info("Gateway conectando ao broker MQTT...");
        mqttClient.connect();
        logger.info("Gateway conectado.");
        mqttClient.subscribe(DRONE_TOPIC_FILTER, 1);
        logger.info("Gateway inscrito no tópico de drones: {}", DRONE_TOPIC_FILTER);
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
            System.out.println("Gateway está inscrito no tópico: " + DRONE_TOPIC_FILTER);
        } catch (MqttException e) {
            logger.error("Erro ao iniciar o Gateway: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.warn("Conexão com o broker MQTT perdida! Causa: {}", cause.getMessage());
        // Aqui irá ser implementada a lógica de reconexão, se necessário.
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        processingExecutor.submit(() -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            logger.debug("Mensagem recebida do tópico {}: {}", topic, payload);
            System.out.println("\nMensagem recebida do tópico " + topic + ": " + payload);
            try {
                String region = topic.substring(topic.lastIndexOf('/') + 1);
                ClimateData data = parseDroneData(region, payload);
                if (data == null) {
                    logger.warn("Não foi possível parsear dados da região {}: {}", region, payload);
                    return;
                }

                inMemoryDatabase.saveData(data.region(), data.toString());
                logger.info("Gateway armazenou dados de {}: {}", region, data);

                publishToRabbitMQ(data);

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
            // Mensagem precisa da região para o dashboard poder agrupar
            String message = data.region() + "|" + data.toString();
            // MUDANÇA: Publica na exchange, não em uma fila. A routingKey é ignorada em fanout.
            rabbitChannel.basicPublish(RABBITMQ_EXCHANGE_NAME, "", null, message.getBytes(StandardCharsets.UTF_8));
            logger.info("Gateway publicou para Exchange RabbitMQ '{}': {}", RABBITMQ_EXCHANGE_NAME, message);
        } catch (IOException e) {
            logger.error("Falha ao publicar no RabbitMQ: {}", e.getMessage());
        }
    }

    private void publishToMqtt(ClimateData data) {
        try {
            // Para o MQTT em tempo real, a região já está no tópico, então enviamos apenas os dados.
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

            // Garante que os recursos sejam fechados ao encerrar a aplicação
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
