package com.climate.data.utils;

import java.util.Locale;

public record ClimateData(
        String region,
        double temperature,
        double humidity,
        double pressure,
        double radiation
        ) {

    @Override
    public String toString() {
        return String.format(Locale.US, "[%s//%.2f//%.2f//%.2f//%.2f]",
                region, temperature, humidity, pressure, radiation);
    }
}
