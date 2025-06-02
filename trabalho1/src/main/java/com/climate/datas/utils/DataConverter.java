package com.climate.datas.utils;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DataConverter {

    public static String convertToStandardFormat(String rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return "";
        }

        // Remove caracteres indesejados como { } ( )
        String cleanedData = rawData.replaceAll("[{}()]", "");

        // Regex para capturar números corretamente, incluindo negativos e decimais
        Pattern numberPattern = Pattern.compile("-?\\d+(\\.\\d+)?");

        // Matcher para encontrar os números no texto
        Matcher matcher = numberPattern.matcher(cleanedData);

        // Coletando todos os números encontrados
        List<String> numbers = matcher.results()
                .map(MatchResult::group)
                .collect(Collectors.toList());

        // Montando a saída no formato padrão
        return "[" + String.join("//", numbers) + "]";
    }
}
