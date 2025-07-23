package com.climate.data.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.climate.data.utils.ClimateData;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class RabbitMQConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConsumerService.class);
    private static final String RABBITMQ_EXCHANGE_NAME = "climate_data_topic_exchange";

    // Base de dados em memória thread-safe
    private final List<ClimateData> database = new CopyOnWriteArrayList<>();
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    private static final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

    public List<ClimateData> getDatabase() {
        return database;
    }

    @PostConstruct
    public void init() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            rabbitConnection = factory.newConnection();
            rabbitChannel = rabbitConnection.createChannel();

            rabbitChannel.exchangeDeclare(RABBITMQ_EXCHANGE_NAME, "topic");
            String queueName = rabbitChannel.queueDeclare().getQueue();

            rabbitChannel.queueBind(queueName, RABBITMQ_EXCHANGE_NAME, "dados.climaticos.#");
            logger.info("Consumidor RabbitMQ aguardando mensagens no binding 'dados.climaticos.#'...");

            DeliverCallback deliverCallback = (_, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                ClimateData data = parseData(message);
                if (data != null) {
                    database.add(data);
                    logger.info("Dado de {} armazenado. Total na base: {}", data.region(), database.size());
                }
            };
            rabbitChannel.basicConsume(queueName, true, deliverCallback, _ -> {
            });

        } catch (IOException | TimeoutException e) {
            logger.error("Falha fatal ao iniciar consumidor RabbitMQ", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (rabbitChannel != null) {
                rabbitChannel.close();
            }
            if (rabbitConnection != null) {
                rabbitConnection.close();
            }
        } catch (IOException | TimeoutException e) {
            logger.error("Erro ao fechar conexão com RabbitMQ", e);
        }
    }

    private ClimateData parseData(String message) {
        try {
            String content = message.replaceAll("[\\[\\]]", "").trim();
            String[] values = content.split("//");

            String region = values[0].trim();
            double temperature = numberFormat.parse(values[1].trim()).doubleValue();
            double humidity = numberFormat.parse(values[2].trim()).doubleValue();
            double pressure = numberFormat.parse(values[3].trim()).doubleValue();
            double radiation = numberFormat.parse(values[4].trim()).doubleValue();

            return new ClimateData(region, pressure, radiation, temperature, humidity);

        } catch (ParseException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            System.err.println("Falha ao parsear a mensagem recebida: " + message);
            return null;
        }
    }
}
