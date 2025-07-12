package com.climate.datas.utils.drone;

import lombok.Getter;

@Getter
public enum RegionFormat {
    NORTE, SUL, LESTE, OESTE;

    public static RegionFormat fromRegionName(String region) {
        return RegionFormat.valueOf(region.toUpperCase());
    }
}
