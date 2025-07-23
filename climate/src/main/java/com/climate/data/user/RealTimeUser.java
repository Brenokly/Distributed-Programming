package com.climate.data.user;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
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

import com.climate.data.config.SharedConfig;
import com.climate.data.utils.ClimateData;

public class RealTimeUser implements MqttCallback, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeUser.class);
    private static final String MQTT_BROKER = SharedConfig.MQTT_BROKER_URL;
    public static final String BASE_TOPIC = "ufersa/pw/gateway/processed_data";

    private static final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

    private final MqttClient mqttClient;
    private final List<ClimateData> receivedData = new CopyOnWriteArrayList<>();
    private final int maxLastMessage = 20;

    public RealTimeUser() throws MqttException {
        String clientId = "realtime-user-" + System.currentTimeMillis();
        mqttClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());
    }

    public void init() {
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
            ClimateData data = parseData(payload);
            addlastMessage(data);
        } catch (Exception e) {
            logger.error("Não foi possível parsear e armazenar os dados em tempo real: {}", payload, e);
        }
    }

    private void addlastMessage(ClimateData data) {
        if (receivedData.size() == maxLastMessage) {
            receivedData.removeFirst();
        }

        receivedData.addLast(data);
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

    private Map<String, Double> calculateAverageByMetric(
            java.util.function.ToDoubleFunction<ClimateData> metricExtractor) {
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
