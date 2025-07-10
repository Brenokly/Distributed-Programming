// package com.climate.datas.user; // Mantenha seu pacote
package com.climate.datas.user;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.utils.ClimateData;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class DashboardUser implements Runnable, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DashboardUser.class);
    private static final String RABBITMQ_HOST = "localhost";
    private static final String QUEUE_NAME = "climate_dashboard_queue";

    private Connection connection;
    private Channel channel;
    private final List<ClimateData> historicalData = new ArrayList<>();

    public DashboardUser() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();
        // Garante que a fila existe. É uma boa prática redeclará-la.
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
    }

    @Override
    public void run() {
        try {
            logger.info("Dashboard-User aguardando mensagens da fila '{}'.", QUEUE_NAME);

            // DeliverCallback é uma interface funcional, perfeita para uma expressão lambda. [cite: 963]
            // Ela define o que fazer quando uma mensagem é recebida.
            DeliverCallback deliverCallback = (_, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                try {
                    // Parseia a mensagem e a adiciona aos dados históricos
                    ClimateData data = parseStandardFormat(message);
                    synchronized (historicalData) {
                        historicalData.add(data);
                    }
                    logger.debug("Dashboard-User recebeu e armazenou: {}", message);
                } catch (Exception e) {
                    logger.error("Erro ao processar mensagem do RabbitMQ: {}", message, e);
                }
            };

            // Começa a consumir da fila
            channel.basicConsume(QUEUE_NAME, true, deliverCallback, _ -> {
            });

        } catch (IOException e) {
            logger.error("Erro no consumidor RabbitMQ: {}", e.getMessage());
        }
    }

    // Parser para o formato padrão: [temperatura | umidade | pressao | radiacao]
    private ClimateData parseStandardFormat(String message) {
        String[] parts = message.split("\\|");
        String region = parts[0];
        String content = parts[1].replace("[", "").replace("]", "");
        String[] valueParts = content.split("\\s*\\|\\s*");

        double temperature = Double.parseDouble(valueParts[0]);
        double humidity = Double.parseDouble(valueParts[1]);
        double pressure = Double.parseDouble(valueParts[2]);
        double solarRadiation = Double.parseDouble(valueParts[3]);

        return new ClimateData(region, temperature, humidity, pressure, solarRadiation, LocalDateTime.now());
    }

    /**
     * Gera e imprime o dashboard consolidado no console. Este método é chamado
     * ao final da simulação.
     */
    public void generateDashboard() {
        logger.info("==================== DASHBOARD CLIMÁTICO FINAL ====================");

        if (historicalData.isEmpty()) {
            logger.info("Nenhum dado foi coletado para gerar o dashboard.");
            logger.info("===================================================================");
            return;
        }

        // Uso da API de Streams para processamento de dados, como solicitado. [cite: 963]
        // Justificativa: Streams fornecem uma maneira declarativa e eficiente para
        // agregar, filtrar e transformar coleções de dados, tornando o código mais legível.
        // 1. Total de dados coletados [cite: 918]
        long totalReadings = historicalData.size();
        logger.info("Total de Leituras Coletadas: {}", totalReadings);

        // 2. Total de dados coletados por elemento climático [cite: 920]
        // Como cada leitura contém os 4 elementos, o total é o mesmo para todos.
        logger.info("Total de Medições por Elemento (Temp, Umid, Press, Rad): {}", totalReadings);

        // 3. Cálculos das médias por região para os rankings
        Map<String, Double> avgTemperatureByRegion = calculateAverageByMetric(ClimateData::temperature);
        Map<String, Double> avgHumidityByRegion = calculateAverageByMetric(ClimateData::humidity);
        Map<String, Double> avgPressureByRegion = calculateAverageByMetric(ClimateData::pressure);
        Map<String, Double> avgRadiationByRegion = calculateAverageByMetric(ClimateData::radiation);

        // 4. Apresentação dos rankings, agora incluindo todos os elementos
        logger.info("\n--- RANKING DE TEMPERATURA MÉDIA (Mais quente para mais frio) ---");
        printSortedMap(avgTemperatureByRegion); // [cite: 923]

        logger.info("\n--- RANKING DE UMIDADE MÉDIA (Mais úmido para mais seco) ---");
        printSortedMap(avgHumidityByRegion); // [cite: 924]

        // CORREÇÃO: Adicionando a impressão dos rankings que faltavam
        logger.info("\n--- RANKING DE PRESSÃO MÉDIA (Mais alta para mais baixa) ---");
        printSortedMap(avgPressureByRegion); // [cite: 925]

        logger.info("\n--- RANKING DE RADIAÇÃO SOLAR MÉDIA (Mais alta para mais baixa) ---");
        printSortedMap(avgRadiationByRegion); // [cite: 926]

        logger.info("===================================================================");
    }

    // Função auxiliar para calcular médias usando streams
    private Map<String, Double> calculateAverageByMetric(java.util.function.ToDoubleFunction<ClimateData> metricExtractor) {
        synchronized (historicalData) {
            return historicalData.stream()
                    .filter(d -> d.region() != null) // Ignora dados sem região
                    .collect(Collectors.groupingBy(
                            ClimateData::region,
                            Collectors.averagingDouble(metricExtractor)
                    ));
        }
    }

    /**
     * Função auxiliar para imprimir um mapa ordenado pelos seus valores em
     * ordem decrescente.
     *
     * @param map O mapa a ser impresso.
     */
    private void printSortedMap(Map<String, Double> map) {
        // CORREÇÃO: O comparador agora é explícito e seguro, resolvendo o aviso de type safety.
        // Usamos Map.Entry.comparingByValue() para indicar que a ordenação é pelo valor da entrada
        // e .reversed() para garantir a ordem decrescente (do maior para o menor).
        map.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(entry -> logger.info(String.format("%-10s: %.2f", entry.getKey(), entry.getValue())));
    }

    @Override
    public void close() throws Exception {
        if (channel.isOpen()) {
            channel.close();
        }
        if (connection.isOpen()) {
            connection.close();
        }
        logger.info("Dashboard-User desconectado.");
    }
}
