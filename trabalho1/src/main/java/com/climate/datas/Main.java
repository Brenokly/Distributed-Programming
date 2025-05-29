package com.climate.datas;

import com.climate.datas.database.DataBase;
import com.climate.datas.drone.Drone;
import com.climate.datas.loadbalancer.LoadBalancer;
import com.climate.datas.server.Server;
import com.climate.datas.utils.drone.DroneId;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        // Inicia o banco de dados
        DataBase dataBase = new DataBase();

        // Cria um pool de threads
        try (
                ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
                LoadBalancer balancer = new LoadBalancer();
                Server server1 = new Server(50001, "225.7.8.9", dataBase);
                Server server2 = new Server(50002, "225.7.8.10", dataBase);
        ) {
            // Inicializa os serviços para ficarem prontos
            balancer.initialize();
            server1.initialize();
            server2.initialize();

            // Agora inicie o loop principal (em threads) — não bloqueante
            executor.execute(balancer::start);
            executor.execute(server1::start);
            executor.execute(server2::start);


            System.out.println("Simulação iniciada.");

            // Solicita o encerramento dos drones após 10 segundos
            executor.schedule(() -> {
                System.out.println("Encerrando drones...");

                System.out.println("Encerrando servidores...");
                server1.close();
                server2.close();

                System.out.println("Encerrando LoadBalancer...");
                balancer.close();

                executor.shutdown();
            }, 10, TimeUnit.SECONDS);

            System.out.println("Simulação finalizada.");
        } catch (IllegalStateException e) {
            System.out.println("Servidor ou LoadBalancer não foi inicializado corretamente: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Erro na simulação: " + e.getMessage());
        }
    }
}
