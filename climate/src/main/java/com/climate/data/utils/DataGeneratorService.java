package com.climate.data.utils;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

@Service
public class DataGeneratorService {

    private final RegionFormat region;

    public DataGeneratorService(RegionFormat region) {
        this.region = region;
    }

    private static final Random random = new Random();

    private static final Map<String, BiFunction<Double, Double, Double>> generators = Map.of(
            "pressure", (min, max) -> min + (max - min) * random.nextDouble(),
            "solarRadiation", (min, max) -> min + (max - min) * random.nextDouble(),
            "temperature", (min, max) -> min + (max - min) * random.nextDouble(),
            "humidity", (min, max) -> min + (max - min) * random.nextDouble()
    );
    private static final Map<String, Map<String, Range>> rangesByRegion = Map.of(
            "NORTE", Map.of(
                    "pressure", new Range(950, 1000), "solarRadiation", new Range(800, 1200),
                    "temperature", new Range(30, 40), "humidity", new Range(70, 90)),
            "SUL", Map.of(
                    "pressure", new Range(1000, 1050), "solarRadiation", new Range(400, 800),
                    "temperature", new Range(10, 20), "humidity", new Range(60, 80)),
            "LESTE", Map.of(
                    "pressure", new Range(970, 1030), "solarRadiation", new Range(600, 1000),
                    "temperature", new Range(25, 35), "humidity", new Range(50, 70)),
            "OESTE", Map.of(
                    "pressure", new Range(980, 1020), "solarRadiation", new Range(500, 900),
                    "temperature", new Range(20, 30), "humidity", new Range(55, 75))
    );

    private double generateValue(String key, Map<String, Range> ranges) {
        Range range = ranges.get(key);
        return generators.get(key).apply(range.min(), range.max());
    }

    private String formatData(double pressure, double solarRadiation, double temperature, double humidity) {
        String[] values = Stream.of(pressure, solarRadiation, temperature, humidity)
                .map(d -> String.format(Locale.US, "%.2f", d))
                .toArray(String[]::new);

        return switch (this.region) {
            case NORTE ->
                String.join("-", values);
            case SUL ->
                String.format("(%s;%s;%s;%s)", (Object[]) values);
            case LESTE ->
                String.format("{%s,%s,%s,%s}", (Object[]) values);
            case OESTE ->
                String.join("#", values);
        };
    }

    public String generateForRegion() {
        Map<String, Range> ranges = rangesByRegion.get(region.name());
        double pressure = generateValue("pressure", ranges);
        double solarRadiation = generateValue("solarRadiation", ranges);
        double temperature = generateValue("temperature", ranges);
        double humidity = generateValue("humidity", ranges);

        return formatData(pressure, solarRadiation, temperature, humidity);
    }
}
