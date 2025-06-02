package com.climate.datas.utils.drone;

public class Generators {

    /**
     * Gera um valor aleatório entre 0 e 1 com 2 casas decimais.
     * É uma interface funcional que pode ser usada para gerar dados aleatórios.
     * Isso torna o código mais flexível e reutilizável, permitindo que diferentes implementações de
     * geração de dados sejam passadas conforme necessário. Sem contar que é um bom princípio de programação.
     *
     * @return DataGenerator que gera valores aleatórios entre 0 e 1.
     */
    public static DataGenerator randomInRange(double min, double max, int casas) {
        double fator = Math.pow(10, casas);
        return () -> {
            double valor = min + Math.random() * (max - min);
            return Math.round(valor * fator) / fator;
        };
    }
}
