package com.climate.datas.user;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.utils.ClimateData; // Ajuste o import se necessário

public class RealTimeUser implements MqttCallback, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeUser.class);
    private static final String MQTT_BROKER = "tcp://broker.emqx.io:1883";
    public static final String BASE_TOPIC = "ufersa/pw/gateway/processed_data";

    private final MqttClient mqttClient;
    private final List<ClimateData> receivedData = new CopyOnWriteArrayList<>();

    public RealTimeUser() throws MqttException {
        String clientId = "realtime-user-" + System.currentTimeMillis();
        mqttClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());
        mqttClient.setCallback(this);
    }

    public void connectAndSubscribe(String topicFilter) throws MqttException {
        logger.info("RealTime-User conectando ao broker MQTT...");
        mqttClient.connect();
        logger.info("RealTime-User conectado.");
        logger.info("Inscrevendo-se no tópico: {}", topicFilter);
        this.mqttClient.subscribe(topicFilter, 1);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        String region = topic.substring(topic.lastIndexOf('/') + 1);

        // 1. Exibe em tempo real no console
        System.out.printf("[TEMPO REAL | %s] -> %s%n", region.toUpperCase(), payload);

        // 2. Parseia e armazena os dados para o dashboard dinâmico
        try {
            ClimateData data = parseRealTimeData(payload);
            receivedData.add(data);
        } catch (Exception e) {
            logger.error("Não foi possível parsear e armazenar os dados em tempo real: {}", payload, e);
        }
    }

    private ClimateData parseRealTimeData(String message) {
        try {
            String content = message.replaceAll("[\\[\\]]", "");
            String[] values = content.split("//");

            String region = values[0];
            double temperature = Double.parseDouble(values[1]);
            double humidity = Double.parseDouble(values[2]);
            double pressure = Double.parseDouble(values[3]);
            double radiation = Double.parseDouble(values[4]);
            LocalDateTime timestamp = LocalDateTime.parse(values[5], DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

            return new ClimateData(region, temperature, humidity, pressure, radiation, timestamp);
        } catch (NumberFormatException e) {
            logger.error("Falha ao parsear a mensagem recebida: {}", message, e);
            return null;
        }
    }

    public String generateDashboardContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("==================== DASHBOARD DINÂMICO (MQTT) ====================\n");

        if (receivedData.isEmpty()) {
            sb.append("Nenhum dado foi coletado para gerar o dashboard.\n");
            sb.append("===================================================================\n");
            return sb.toString();
        }

        long totalReadings = receivedData.size();
        sb.append("Total de Leituras Coletadas: ").append(totalReadings).append("\n");
        sb.append("Total de Medições por Elemento: ").append(totalReadings).append("\n");

        Map<String, Double> avgTemp = calculateAverageByMetric(ClimateData::temperature);
        sb.append("\n--- TEMPERATURA: Ranking de Média e Contribuição Percentual ---\n");
        appendRankings(sb, avgTemp, "°C");

        Map<String, Double> avgHumidity = calculateAverageByMetric(ClimateData::humidity);
        sb.append("\n--- UMIDADE RELATIVA: Ranking de Média e Contribuição Percentual ---\n");
        appendRankings(sb, avgHumidity, "%");

        Map<String, Double> avgPressure = calculateAverageByMetric(ClimateData::pressure);
        sb.append("\n--- PRESSÃO ATMOSFÉRICA: Ranking de Média e Contribuição Percentual ---\n");
        appendRankings(sb, avgPressure, "hPa");

        Map<String, Double> avgRadiation = calculateAverageByMetric(ClimateData::radiation);
        sb.append("\n--- RADIAÇÃO SOLAR: Ranking de Média e Contribuição Percentual ---\n");
        appendRankings(sb, avgRadiation, "W/m²");

        sb.append("===================================================================\n");
        return sb.toString();
    }

    private Map<String, Double> calculateAverageByMetric(java.util.function.ToDoubleFunction<ClimateData> metricExtractor) {
        return receivedData.stream()
                .collect(Collectors.groupingBy(ClimateData::region, Collectors.averagingDouble(metricExtractor)));
    }

    private void appendRankings(StringBuilder sb, Map<String, Double> map, String unit) {
        double totalSumOfAverages = map.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalSumOfAverages == 0) {
            return;
        }

        map.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(entry -> {
                    double percentage = (entry.getValue() / totalSumOfAverages) * 100;
                    sb.append(String.format("%-10s: Média %.2f %s   (%.2f%% do total)\n",
                            entry.getKey(), entry.getValue(), unit, percentage));
                });
    }

    @Override
    public void close() throws Exception {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
        logger.info("RealTime-User desconectado.");
    }

    // Métodos não utilizados, mas necessários pela interface MqttCallback
    @Override
    public void connectionLost(Throwable cause) {
        logger.warn("Conexão perdida.", cause);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

}
