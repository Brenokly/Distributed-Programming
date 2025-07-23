package com.climate.data.drone.execute;

import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.data.drone.Drone;

public class DroneLeste {

    private static final Logger logger = LoggerFactory.getLogger(DroneLeste.class);

    public static void main(String[] args) {
        try {
            Drone drone = new Drone("LESTE");

            // Adiciona um Shutdown Hook para fechar o drone de forma limpa com Ctrl+C
            // Um Shutdown Hook é uma thread que é executada quando o programa é encerrado,
            // permitindo que você libere recursos ou faça limpeza antes de sair.
            Runtime.getRuntime().addShutdownHook(new Thread(drone::close));

            drone.connect();

            // Início interativo da coleta, conforme especificação
            System.out.println("Drone da região Leste está pronto.");
            System.out.println("Pressione Enter para iniciar a coleta e envio de dados...");
            try (Scanner sc = new Scanner(System.in)) {
                sc.nextLine();
            }

            drone.startCollecting();

            // Mantém a thread principal viva enquanto o scheduler trabalha
            // O programa será encerrado com Ctrl+C
            Thread.currentThread().join();
        } catch (MqttException e) {
            logger.error("Falha fatal na conexão MQTT do drone Leste: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
        }
        logger.info("Thread principal do drone Leste interrompida. Encerrando...");
        Thread.currentThread().interrupt();
    }
}
