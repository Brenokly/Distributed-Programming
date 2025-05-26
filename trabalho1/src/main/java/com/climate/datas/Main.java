package com.climate.datas;

import com.climate.datas.loadbalancer.LoadBalancer;

public class Main {
    public static void main(String[] args) {
        LoadBalancer balancer = new LoadBalancer();

        Thread balancerThread = new Thread(balancer::start);
        balancerThread.start();


        try {
            balancerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Simulação finalizada.");
    }

}
