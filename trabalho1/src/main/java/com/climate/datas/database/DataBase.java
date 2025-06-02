package com.climate.datas.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataBase {
    private final Map<String, List<String>> database = new ConcurrentHashMap<>();

    public void saveData(String droneId, String data) {
        if (droneId == null || data == null) {
            throw new IllegalArgumentException("DroneId ou dados não podem ser nulos.");
        }

        database.computeIfAbsent(droneId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(data);
    }

    public List<String> getData(String droneId) {
        return database.getOrDefault(droneId, List.of());
    }

    public Map<String, List<String>> getAllData() {
        return Collections.unmodifiableMap(database);
    }

    public void printAllData() {
        System.out.println("Dados armazenados no banco de dados:");
        database.forEach((drone, dataList) -> {
            System.out.println("Drone: " + drone);
            dataList.forEach(System.out::println);
        });
    }
}
