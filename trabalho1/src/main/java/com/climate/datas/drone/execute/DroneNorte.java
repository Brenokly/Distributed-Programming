package com.climate.datas.drone.execute;

import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.climate.datas.drone.Drone;

public class DroneNorte {

    private static final Logger logger = LoggerFactory.getLogger(DroneNorte.class);

    public static void main(String[] args) {
        try {
            Drone drone = new Drone("NORTE");

            // Adiciona um Shutdown Hook para fechar o drone de forma limpa com Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(drone::close));

            drone.connect();

            // Início interativo da coleta, conforme especificação
            System.out.println("Drone da região Norte está pronto.");
            System.out.println("Pressione Enter para iniciar a coleta e envio de dados...");
            new Scanner(System.in).nextLine();

            drone.startCollecting();

            // Mantém a thread principal viva enquanto o scheduler trabalha
            // O programa será encerrado com Ctrl+C
            Thread.currentThread().join();

        } catch (MqttException e) {
            // Agora o logger funciona aqui
            logger.error("Falha fatal na conexão MQTT do drone Norte: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            // E aqui também
            logger.info("Thread principal do drone Norte interrompida. Encerrando...");
            Thread.currentThread().interrupt();
        }
    }
}
