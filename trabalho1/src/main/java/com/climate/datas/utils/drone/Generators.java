package com.climate.datas.utils.drone;

public class Generators implements DataGenerator {

    @Override
    public double generate(double min, double max) {
        double fator = Math.pow(10, 2);
        double valor = min + Math.random() * (max - min);
        return DataGenerator.generateWithRange(fator, valor);
    }
}
