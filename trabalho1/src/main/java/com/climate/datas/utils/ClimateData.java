package com.climate.datas.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale; // << ADICIONE ESTE IMPORT

public record ClimateData(
        String region,
        double temperature,
        double humidity,
        double pressure,
        double radiation,
        LocalDateTime timestamp
        ) {

    // CORREÇÃO: Vamos usar um padrão com milissegundos para evitar erros
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public String toString() {
        // CORREÇÃO: Adicionado Locale.US para garantir o ponto como separador decimal
        return String.format(Locale.US, "[%s//%.2f//%.2f//%.2f//%.2f//%s]",
                region, temperature, humidity, pressure, radiation, timestamp.format(dtf));
    }
}
