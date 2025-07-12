package com.climate.datas.user.execute;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.user.RealTimeUser;

public class RealTimeUserLauncher {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeUserLauncher.class);

    public static void main(String[] args) {
        System.out.println("--- Real-Time User (Consumidor MQTT) ---");
        Scanner scanner = new Scanner(System.in);
        RealTimeUser user = null;

        try {
            user = new RealTimeUser();

            // Pergunta qual tópico assinar
            System.out.println("Escolha a região para monitorar em tempo real:");
            System.out.println("1: Todas as Regiões");
            System.out.println("2: Apenas Região NORTE");
            System.out.println("3: Apenas Região SUL");
            System.out.println("4: Apenas Região LESTE");
            System.out.println("5: Apenas Região OESTE");
            System.out.print("Opção: ");
            int choice = Integer.parseInt(scanner.nextLine());

            String topicFilter = switch (choice) {
                case 1 ->
                    RealTimeUser.BASE_TOPIC + "/#";
                case 2 ->
                    RealTimeUser.BASE_TOPIC + "/norte";
                case 3 ->
                    RealTimeUser.BASE_TOPIC + "/sul";
                case 4 ->
                    RealTimeUser.BASE_TOPIC + "/leste";
                case 5 ->
                    RealTimeUser.BASE_TOPIC + "/oeste";
                default ->
                    RealTimeUser.BASE_TOPIC + "/#";
            };

            user.connectAndSubscribe(topicFilter);
            System.out.println("Monitorando dados em tempo real...\n");

            // Adiciona o Shutdown Hook para gerar o relatório final
            final RealTimeUser finalUser = user;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nEncerrando... Gerando relatório final em 'dashboard_final_mqtt.txt'");
                saveDashboardToFile(finalUser.generateDashboardContent(), "dashboard_final_mqtt.txt");
            }));

            int action = -1;
            while (action != 0) {
                System.out.println("\n--- MENU DE AÇÕES (Real-Time) ---");
                System.out.println("1: Gerar Dashboard em arquivo .txt agora");
                System.out.println("0: Sair");
                System.out.print("Sua escolha: ");

                try {
                    action = Integer.parseInt(scanner.nextLine());
                } catch (NumberFormatException e) {
                    System.out.println("Por favor, digite um número válido.");
                    continue;
                }

                if (action == 1) {
                    String fileName = "dashboard_realtime_" + System.currentTimeMillis() + ".txt";
                    System.out.println("Gerando dashboard em '" + fileName + "'...");
                    saveDashboardToFile(user.generateDashboardContent(), fileName);
                    System.out.println("Arquivo gerado com sucesso!");
                }
            }

        } catch (NumberFormatException | MqttException e) {
            logger.error("Falha fatal no RealTime User: {}", e.getMessage(), e);
        } finally {
            if (user != null) {
                try {
                    user.close();
                } catch (Exception e) {
                    logger.error("Erro ao fechar o RealTimeUser.", e);
                }
            }
            scanner.close();
        }
    }

    private static void saveDashboardToFile(String content, String fileName) {
        try (PrintWriter out = new PrintWriter(new FileWriter(fileName))) {
            out.println(content);
        } catch (IOException e) {
            logger.error("Erro ao salvar dashboard no arquivo '{}'", fileName, e);
        }
    }
}
