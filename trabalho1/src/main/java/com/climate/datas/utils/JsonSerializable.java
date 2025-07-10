package com.climate.datas.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public interface JsonSerializable {

    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    default String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            // Implementando disponibilidade
            System.out.println("Erro ao serializar JSON");
            return "Erro ao serializar JSON";
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    static <T> T fromJson(String json, Class<T> clas) {
        try {
            return objectMapper.readValue(json, clas);
        } catch (Exception e) {
            System.out.println("Erro ao deserializar JSON");
            return null;
        }
    }

    static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            System.out.println("Erro ao deserializar JSON");
            return null;
        }
    }
}
