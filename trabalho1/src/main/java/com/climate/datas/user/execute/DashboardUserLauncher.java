package com.climate.datas.user.execute;

import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.user.DashboardUser;

public class DashboardUserLauncher {

    private static final Logger logger = LoggerFactory.getLogger(DashboardUserLauncher.class);

    public static void main(String[] args) {
        System.out.println("--- Dashboard User (Consumidor RabbitMQ) ---");
        Scanner scanner = new Scanner(System.in);

        // Menu para escolher o filtro de tópico inicial
        System.out.println("Escolha os dados que deseja receber:");
        System.out.println("1: Todos os dados de todas as regiões");
        System.out.println("2: Apenas dados da região NORTE");
        // ... (adicione outras opções se desejar)
        System.out.print("Opção de subscrição: ");
        int subscriptionChoice = Integer.parseInt(scanner.nextLine());

        String bindingKey;
        if (subscriptionChoice == 1) {
            bindingKey = "dados.climaticos.#";
        } else if (subscriptionChoice == 2) {
            bindingKey = "dados.climaticos.norte";
        } else {
            System.out.println("Opção inválida. Assinando para todos os dados por padrão.");
            bindingKey = "dados.climaticos.#";
        }

        try {
            DashboardUser dashboardUser = new DashboardUser();

            // Inicia o consumidor em uma nova thread para não bloquear o menu
            new Thread(() -> {
                try {
                    dashboardUser.startListening(bindingKey);
                } catch (Exception e) {
                    logger.error("Erro no thread consumidor do RabbitMQ.", e);
                }
            }).start();

            // Loop do menu interativo principal
            int userAction = -1;
            while (userAction != 0) {
                System.out.println("\n--- MENU DE AÇÕES ---");
                System.out.println("1: Gerar Dashboard Atual");
                System.out.println("2: Buscar dados por Região");
                System.out.println("0: Sair");
                System.out.print("Sua escolha: ");

                userAction = Integer.parseInt(scanner.nextLine());

                switch (userAction) {
                    case 1 ->
                        dashboardUser.generateDashboard();
                    case 2 -> {
                        System.out.print("Digite a região para buscar (NORTE, SUL, LESTE, OESTE): ");
                        String region = scanner.nextLine();
                        dashboardUser.searchByRegion(region);
                    }
                    case 0 ->
                        System.out.println("Encerrando...");
                    default ->
                        System.out.println("Opção inválida. Tente novamente.");
                }
            }

            dashboardUser.close(); // Fecha os recursos ao sair do loop

        } catch (Exception e) {
            logger.error("Falha fatal ao iniciar o Dashboard User: {}", e.getMessage(), e);
        } finally {
            scanner.close();
        }
    }
}
