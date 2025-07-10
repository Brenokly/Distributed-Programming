package com.climate.datas.main;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.drone.Drone;
import com.climate.datas.gateway.Gateway;
import com.climate.datas.user.DashboardUser;
import com.climate.datas.user.RealTimeUser;

public class SimulationMain {

    private static final Logger logger = LoggerFactory.getLogger(SimulationMain.class);
    private static final int SIMULATION_DURATION_MINUTES = 3;

    public static void main(String[] args) {
        System.out.println("Pressione '1' e Enter para iniciar a simulação...");
        Scanner scanner = new Scanner(System.in);
        if (!scanner.nextLine().equals("1")) {
            System.out.println("Simulação cancelada.");
            scanner.close();
            return;
        }

        logger.info("Iniciando a simulação...");

        Gateway gateway = null;
        DashboardUser dashboardUser = null;
        RealTimeUser realTimeUser = null;
        ExecutorService droneExecutor = Executors.newFixedThreadPool(4);
        ExecutorService userExecutor = Executors.newFixedThreadPool(2);

        try {
            // 1. Inicia os componentes centrais
            gateway = new Gateway();
            dashboardUser = new DashboardUser();
            realTimeUser = new RealTimeUser();

            new Thread(gateway, "Gateway-Thread").start();
            logger.info("Gateway iniciado.");

            // 2. Inicia os drones
            List<Drone> drones = List.of(new Drone("NORTE"), new Drone("SUL"), new Drone("LESTE"), new Drone("OESTE"));
            drones.forEach(droneExecutor::submit);
            logger.info("Drones iniciados.");

            // 3. Aguarda 10 segundos para iniciar os usuários
            logger.info("Aguardando 10 segundos para iniciar os usuários...");
            TimeUnit.SECONDS.sleep(10);

            // 4. Inicia os usuários
            userExecutor.submit(dashboardUser);
            userExecutor.submit(realTimeUser);
            logger.info("Usuários (Dashboard e Tempo Real) iniciados.");

            // 5. Aguarda o fim do tempo de simulação
            logger.info("A simulação será executada por {} minutos.", SIMULATION_DURATION_MINUTES);
            TimeUnit.MINUTES.sleep(SIMULATION_DURATION_MINUTES);

        } catch (IOException | InterruptedException | TimeoutException | MqttException e) {
            logger.error("Ocorreu um erro crítico na simulação: {}", e.getMessage(), e);
        } finally {
            logger.info("Tempo de simulação encerrado. Finalizando todos os componentes...");

            // 6. Gera o dashboard final antes de encerrar
            if (dashboardUser != null) {
                dashboardUser.generateDashboard();
            }

            // Encerra os executors e componentes
            droneExecutor.shutdownNow();
            userExecutor.shutdownNow();
            closeComponent(gateway);
            closeComponent(dashboardUser);
            closeComponent(realTimeUser);

            scanner.close();
            logger.info("Simulação finalizada.");
            // Força o encerramento do programa, já que o Scanner no RealTimeUser pode prender o processo.
            System.exit(0);
        }
    }

    private static void closeComponent(AutoCloseable component) {
        if (component != null) {
            try {
                component.close();
            } catch (Exception e) {
                logger.error("Erro ao fechar componente {}: {}", component.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
