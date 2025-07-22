package com.climate.datas.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataBase {

    // Usando ConcurrentHashMap para segurança em ambiente com múltiplas threads
    private final Map<String, List<String>> storage = new ConcurrentHashMap<>();

    public void saveData(String region, String data) {
        if (region == null || data == null) {
            throw new IllegalArgumentException("Região ou dados não podem ser nulos.");
        }

        // Adiciona o dado na lista da região correspondente. Se não existir, cria a lista.
        storage.computeIfAbsent(region, _ -> new ArrayList<>()).add(data);
    }

    public List<String> getData(String region) {
        return storage.getOrDefault(region, List.of());
    }

    public Map<String, List<String>> getAllData() {
        return new ConcurrentHashMap<>(storage); // Retorna uma cópia para evitar modificação externa
    }

    public void printAllData() {
        System.out.println("Dados armazenados no banco de dados:");
        storage.forEach((region, dataList) -> {
            System.out.println("Região: " + region);
            dataList.forEach(System.out::println);
        });
    }
}
