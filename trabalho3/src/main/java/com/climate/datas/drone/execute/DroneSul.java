package com.climate.datas.drone.execute;

import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.drone.Drone;

public class DroneSul {

    private static final Logger logger = LoggerFactory.getLogger(DroneSul.class);

    public static void main(String[] args) {
        System.out.println("--- Lançador do Drone SUL ---");
        Scanner scanner = new Scanner(System.in);
        Drone drone = null;

        try {
            // Cria e conecta o Drone apenas UMA VEZ
            drone = new Drone("SUL");
            Runtime.getRuntime().addShutdownHook(new Thread(drone::close)); // Para fechar com Ctrl+C
            drone.connect();

            int option = -1;
            boolean isCollecting = false;

            while (option != 0) {
                System.out.println("\n--- MENU DE CONTROLE DO DRONE SUL ---");
                System.out.println("1 - Iniciar Coleta de Dados");
                System.out.println("2 - Simular Falha de Conexão");
                System.out.println("0 - Encerrar Drone");
                System.out.print("Opção: ");

                option = Integer.parseInt(scanner.nextLine());

                switch (option) {
                    case 1:
                        if (!isCollecting) {
                            drone.startCollecting();
                            isCollecting = true;
                        } else {
                            System.out.println("A coleta já está em andamento.");
                        }
                        break;
                    case 2:
                        if (isCollecting) {
                            drone.simulateMqttFailure();
                            isCollecting = false;
                            System.out.println("FALHA SIMULADA. O drone está desconectado e não irá reconectar (AutomaticReconnect=false).");
                        } else {
                            System.out.println("A coleta não está ativa para simular uma falha.");
                        }
                        break;
                    case 0:
                        System.out.println("Encerrando...");
                        break;
                    default:
                        System.out.println("Opção inválida.");
                        break;
                }
            }

        } catch (NumberFormatException | MqttException e) {
            logger.error("Falha fatal na aplicação do Drone: {}", e.getMessage(), e);
        } finally {
            if (drone != null) {
                drone.close();
            }
            scanner.close();
        }
    }
}
