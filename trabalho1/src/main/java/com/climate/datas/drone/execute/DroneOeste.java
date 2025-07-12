package com.climate.datas.drone.execute;

import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.drone.Drone;

public class DroneOeste {

    private static final Logger logger = LoggerFactory.getLogger(DroneOeste.class);

    public static void main(String[] args) {
        try {
            Drone drone = new Drone("OESTE");

            // Adiciona um Shutdown Hook para fechar o drone de forma limpa com Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(drone::close));

            drone.connect();

            // Início interativo da coleta, conforme especificação
            System.out.println("Drone da região Oeste está pronto.");
            System.out.println("Pressione Enter para iniciar a coleta e envio de dados...");
            new Scanner(System.in).nextLine();

            drone.startCollecting();

            // O programa será encerrado com Ctrl+C
            Thread.currentThread().join();

        } catch (MqttException e) {
            logger.error("Falha fatal na conexão MQTT do drone Oeste: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.info("Thread principal do drone Oeste interrompida. Encerrando...");
            Thread.currentThread().interrupt();
        }
    }
}
