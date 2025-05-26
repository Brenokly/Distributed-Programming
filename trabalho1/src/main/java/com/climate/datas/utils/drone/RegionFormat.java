package com.climate.datas.utils.drone;

import com.climate.datas.utils.DataFormatter;
import lombok.Getter;

@Getter
public enum RegionFormat {
    NORTE("Norte", values -> String.join("-", values)),
    SUL("Sul", values -> "(" + String.join(";", values) + ")"),
    LESTE("Leste", values -> "{" + String.join(",", values) + "}"),
    OESTE("Oeste", values -> String.join("#", values));

    private final String value;
    private final DataFormatter formatter;

    RegionFormat(String value, DataFormatter formatter) {
        this.value = value;
        this.formatter = formatter;
    }

    public String format(String[] values) {
        return formatter.format(values);
    }
}
