package com.climate.datas.utils.drone;

@FunctionalInterface
public interface DataGenerator {
    double generate(double min, double max);

    static double generateWithRange(double fator, double valor) {
        return Math.round(valor * fator) / fator;
    }
}