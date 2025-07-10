package com.climate.datas.utils;

import java.time.LocalDateTime;

public record ClimateData(String region, double temperature, double humidity, double pressure, double radiation, LocalDateTime timestamp) {

    @Override
    public String toString() {
        return "[" + region + "//" + temperature + "//" + humidity + "//" + pressure + "//" + radiation + "//" + timestamp + "]";
    }
}
