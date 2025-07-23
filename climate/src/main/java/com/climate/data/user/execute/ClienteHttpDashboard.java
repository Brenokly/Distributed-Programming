package com.climate.data.user.execute;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ClienteHttpDashboard {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String BASE_URL = "http://localhost:8080";
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n--- Cliente Dashboard HTTP ---");
                System.out.println("Escolha a região para consultar:");
                System.out.println("1. Todas as Regiões");
                System.out.println("2. Norte");
                System.out.println("3. Sul");
                System.out.println("4. Leste");
                System.out.println("5. Oeste");
                System.out.println("0. Sair");
                System.out.print("Opção: ");

                String url = switch (scanner.nextLine()) {
                    case "1" ->
                        BASE_URL + "/dashboard";
                    case "2" ->
                        BASE_URL + "/dashboard/norte";
                    case "3" ->
                        BASE_URL + "/dashboard/sul";
                    case "4" ->
                        BASE_URL + "/dashboard/leste";
                    case "5" ->
                        BASE_URL + "/dashboard/oeste";
                    case "0" ->
                        null;
                    default ->
                        "invalido";
                };

                if (url == null) {
                    break;
                }
                if (url.equals("invalido")) {
                    System.out.println("Opção inválida.");
                    continue;
                }

                fetchDashboardData(url).join();
            }
        }
        System.out.println("Cliente finalizado.");
    }

    private static CompletableFuture<Void> fetchDashboardData(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .build();

        System.out.println("Buscando dados de forma assíncrona...");

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(jsonBody -> {
                    System.out.println("\n--- RESULTADO DO DASHBOARD ---");
                    String formattedDashboard = formatDashboardFromJson(jsonBody);
                    System.out.println(formattedDashboard);
                    System.out.println("----------------------------");
                })
                .exceptionally(ex -> {
                    System.err.println("Falha ao buscar dados: " + ex.getCause().getMessage());
                    return null;
                });
    }

    /**
     * Converte a string JSON recebida da API em um dashboard de texto
     * formatado.
     *
     * @param jsonBody O corpo da resposta HTTP em formato JSON.
     * @return Uma String formatada para exibição no console.
     */
    private static String formatDashboardFromJson(String jsonBody) {
        var type = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> dataMap = gson.fromJson(jsonBody, type);

        if (dataMap.containsKey("message")) {
            return (String) dataMap.get("message");
        }

        var sb = new StringBuilder();
        sb.append("==================== DASHBOARD HISTÓRICO (HTTP) ====================\n");

        long totalReadings = ((Number) dataMap.get("totalDadosColetados")).longValue();
        sb.append("Total de Leituras Coletadas: ").append(totalReadings).append("\n");
        sb.append("Total de Medições por Elemento: ").append(totalReadings).append("\n");

        List<Map<String, Object>> rankingTemperatura = gson.fromJson(
                gson.toJson(dataMap.get("rankingTemperatura")),
                new TypeToken<List<Map<String, Object>>>() {
                }.getType()
        );
        List<Map<String, Object>> rankingUmidade = gson.fromJson(
                gson.toJson(dataMap.get("rankingUmidade")),
                new TypeToken<List<Map<String, Object>>>() {
                }.getType()
        );
        List<Map<String, Object>> rankingPressao = gson.fromJson(
                gson.toJson(dataMap.get("rankingPressao")),
                new TypeToken<List<Map<String, Object>>>() {
                }.getType()
        );
        List<Map<String, Object>> rankingRadiacao = gson.fromJson(
                gson.toJson(dataMap.get("rankingRadiacao")),
                new TypeToken<List<Map<String, Object>>>() {
                }.getType()
        );

        appendRankings(sb, rankingTemperatura, "TEMPERATURA", "°C");
        appendRankings(sb, rankingUmidade, "UMIDADE RELATIVA", "%");
        appendRankings(sb, rankingPressao, "PRESSÃO ATMOSFÉRICA", "hPa");
        appendRankings(sb, rankingRadiacao, "RADIAÇÃO SOLAR", "W/m²");

        sb.append("===================================================================\n");
        return sb.toString();
    }

    private static void appendRankings(StringBuilder sb, List<Map<String, Object>> ranking, String title, String unit) {
        sb.append(String.format("\n--- %s: Ranking de Média e Contribuição Percentual ---\n", title));
        if (ranking == null || ranking.isEmpty()) {
            sb.append("Dados insuficientes.\n");
            return;
        }

        ranking.forEach(entry -> {
            String region = (String) entry.get("regiao");
            String avg = (String) entry.get("media");
            String percent = (String) entry.get("percentual");

            sb.append(String.format("%-10s: Média %s %s   (%s do total)\n",
                    region, avg, unit, percent));
        });
    }
}
