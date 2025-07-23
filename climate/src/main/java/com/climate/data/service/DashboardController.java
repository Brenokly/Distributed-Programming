package com.climate.data.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.climate.data.utils.ClimateData;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class DashboardController {

    private final RabbitMQConsumerService consumerService;

    public DashboardController(RabbitMQConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @GetMapping("/dashboard")
    public Mono<Map<String, Object>> getFullDashboard() {
        return generateDashboard(Flux.fromIterable(consumerService.getDatabase()));
    }

    @GetMapping("/dashboard/{region}")
    public Mono<Map<String, Object>> getDashboardByRegion(@PathVariable String region) {
        Flux<ClimateData> regionData = Flux.fromIterable(consumerService.getDatabase())
                .filter(data -> data.region().equalsIgnoreCase(region));
        return generateDashboard(regionData);
    }

    private Mono<Map<String, Object>> generateDashboard(Flux<ClimateData> dataFlux) {
        return dataFlux.collectList().map(dataList -> {
            if (dataList.isEmpty()) {
                return Map.of("message", "Nenhum dado encontrado para os filtros aplicados.");
            }

            long totalData = dataList.size();
            Map<String, Double> avgTemp = calculateAverage(dataList, ClimateData::temperature);
            Map<String, Double> avgHumidity = calculateAverage(dataList, ClimateData::humidity);
            Map<String, Double> avgPressure = calculateAverage(dataList, ClimateData::pressure);
            Map<String, Double> avgRadiation = calculateAverage(dataList, ClimateData::radiation);

            return Map.of(
                    "totalDadosColetados", totalData,
                    "totalDadosPorElemento", totalData,
                    "rankingTemperatura", createRanking(avgTemp),
                    "rankingUmidade", createRanking(avgHumidity),
                    "rankingPressao", createRanking(avgPressure),
                    "rankingRadiacao", createRanking(avgRadiation)
            );
        });
    }

    private Map<String, Double> calculateAverage(List<ClimateData> dataList, java.util.function.ToDoubleFunction<ClimateData> metricExtractor) {
        return dataList.stream()
                .collect(Collectors.groupingBy(ClimateData::region, Collectors.averagingDouble(metricExtractor)));
    }

    private List<Map<String, Object>> createRanking(Map<String, Double> averages) {
        double totalSum = averages.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalSum == 0) {
            return List.of();
        }

        return averages.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> rankingEntry = new HashMap<>();
                    rankingEntry.put("regiao", entry.getKey());
                    rankingEntry.put("media", String.format("%.2f", entry.getValue()));
                    rankingEntry.put("percentual", String.format("%.2f%%", (entry.getValue() / totalSum) * 100));
                    return rankingEntry;
                })
                .collect(Collectors.toList());
    }
}
