package com.climate.datas.utils.drone;

import com.climate.datas.utils.JsonSerializable;
import lombok.Getter;

@Getter
public enum DroneId implements JsonSerializable {
    NORTE("Norte"), SUL("Sul"), LESTE("Lest"), OESTE("Oeste");

    final String value;

    DroneId(String value) {
        this.value = value;
    }
}