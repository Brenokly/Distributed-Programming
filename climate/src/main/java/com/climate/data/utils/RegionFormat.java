package com.climate.data.utils;

import lombok.Getter;

@Getter
public enum RegionFormat {
    NORTE("Norte"), SUL("Sul"), LESTE("Leste"), OESTE("Oeste");

    private final String displayName;

    RegionFormat(String displayName) {
        this.displayName = displayName;
    }

    public static RegionFormat fromRegionName(String region) {
        return RegionFormat.valueOf(region.toUpperCase());
    }

    public String toUpperCase() {
        return this.displayName.toUpperCase();
    }

    public String toLowerCase() {
        return this.displayName.toLowerCase();
    }

}
