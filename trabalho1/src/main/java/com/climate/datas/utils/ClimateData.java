package com.climate.datas.utils;

public record ClimateData(double temperatura, double umidade, double pressao, double radiacao) {

    @Override
    public String toString() {
        return "[" + temperatura + "//" + umidade + "//" + pressao + "//" + radiacao + "]";
    }
}
