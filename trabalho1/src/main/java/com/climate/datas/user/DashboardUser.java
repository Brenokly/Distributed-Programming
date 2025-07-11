package com.climate.datas.user;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.utils.ClimateData; // Ajuste o import
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class DashboardUser implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DashboardUser.class);
    private static final String RABBITMQ_HOST = "localhost";
    private static final String EXCHANGE_NAME = "climate_data_topic_exchange";

    private Connection connection;
    private Channel channel;
    private final List<ClimateData> historicalData = new CopyOnWriteArrayList<>();

    public DashboardUser() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();
    }

    /**
     * Inicia o processo de escuta das mensagens do RabbitMQ em segundo plano.
     */
    public void startListening(String bindingKey) throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
        logger.info("Dashboard-User está ouvindo a exchange '{}' com o filtro '{}'.", EXCHANGE_NAME, bindingKey);

        DeliverCallback deliverCallback = (_, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try {
                ClimateData data = parseStandardFormat(message);
                historicalData.add(data);
                logger.trace("Dado recebido e armazenado: {}", data);
            } catch (Exception e) {
                logger.error("Erro ao processar mensagem: {}", message, e);
            }
        };

        channel.basicConsume(queueName, true, deliverCallback, _ -> {
        });
    }

    private ClimateData parseStandardFormat(String message) {
        String[] parts = message.split("\\|");
        String region = parts[0];
        String content = parts[1].replace("[", "").replace("]", "");
        String[] valueParts = content.split("\\s*\\|\\s*");
        return new ClimateData(region, Double.parseDouble(valueParts[0]), Double.parseDouble(valueParts[1]), Double.parseDouble(valueParts[2]), Double.parseDouble(valueParts[3]), LocalDateTime.now());
    }

    // ====================================================================
    // MÉTODOS PARA INTERAÇÃO E DASHBOARD
    // ====================================================================
    /**
     * Gera e imprime o dashboard com os dados coletados até o momento.
     */
    public void generateDashboard() {
        System.out.println("\n==================== DASHBOARD CLIMÁTICO ATUAL ====================");
        if (historicalData.isEmpty()) {
            System.out.println("Nenhum dado foi coletado para gerar o dashboard.");
            System.out.println("===================================================================");
            return;
        }

        long totalReadings = historicalData.size();
        System.out.println("Total de Leituras Coletadas: " + totalReadings);
        System.out.println("Total de Medições por Elemento: " + totalReadings);

        Map<String, Double> avgTemp = calculateAverageByMetric(ClimateData::temperature);
        System.out.println("\n--- TEMPERATURA: Ranking de Média e Contribuição Percentual ---");
        printRankingsAndPercentages(avgTemp, "°C");

        Map<String, Double> avgHumidity = calculateAverageByMetric(ClimateData::humidity);
        System.out.println("\n--- UMIDADE RELATIVA: Ranking de Média e Contribuição Percentual ---");
        printRankingsAndPercentages(avgHumidity, "%");

        Map<String, Double> avgPressure = calculateAverageByMetric(ClimateData::pressure);
        System.out.println("\n--- PRESSÃO ATMOSFÉRICA: Ranking de Média e Contribuição Percentual ---");
        printRankingsAndPercentages(avgPressure, "hPa");

        Map<String, Double> avgRadiation = calculateAverageByMetric(ClimateData::radiation);
        System.out.println("\n--- RADIAÇÃO SOLAR: Ranking de Média e Contribuição Percentual ---");
        printRankingsAndPercentages(avgRadiation, "W/m²");

        System.out.println("===================================================================\n");
    }

    /**
     * Realiza uma busca na base de dados em memória e imprime os resultados.
     */
    public void searchByRegion(String region) {
        System.out.printf("\n--- BUSCANDO DADOS PARA A REGIÃO: %s ---\n", region.toUpperCase());
        List<ClimateData> results = historicalData.stream()
                .filter(data -> region.equalsIgnoreCase(data.region()))
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            System.out.println("Nenhum dado encontrado para esta região.");
        } else {
            results.forEach(System.out::println);
        }
        System.out.println("--- FIM DA BUSCA ---\n");
    }

    private Map<String, Double> calculateAverageByMetric(java.util.function.ToDoubleFunction<ClimateData> metricExtractor) {
        return historicalData.stream()
                .collect(Collectors.groupingBy(ClimateData::region, Collectors.averagingDouble(metricExtractor)));
    }

    private void printRankingsAndPercentages(Map<String, Double> map, String unit) {
        double totalSumOfAverages = map.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalSumOfAverages == 0) {
            return;
        }

        map.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(entry -> {
                    double percentage = (entry.getValue() / totalSumOfAverages) * 100;
                    System.out.printf("%-10s: Média %.2f %s   (%.2f%% do total)\n",
                            entry.getKey(), entry.getValue(), unit, percentage);
                });
    }

    @Override
    public void close() throws Exception {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
        logger.info("Dashboard-User desconectado.");
    }
}
