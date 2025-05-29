package com.climate.datas.utils.drone;

import com.climate.datas.utils.JsonSerializable;
import lombok.Getter;

@Getter
public enum DroneId implements JsonSerializable {
    NORTE("Norte", 0), SUL("Sul", 1), LESTE("Leste", 2), OESTE("Oeste", 3);

    final String value;
    final int ordinal;

    DroneId(String value, int ordinal) {
        this.value = value;
        this.ordinal = ordinal;
    }
}