package com.climate.datas.utils.drone;

public class Generators {

    public static DataGenerator randomInRange(double min, double max, int casas) {
        double fator = Math.pow(10, casas);
        return () -> {
            double valor = min + Math.random() * (max - min);
            return Math.round(valor * fator) / fator;
        };
    }
}
