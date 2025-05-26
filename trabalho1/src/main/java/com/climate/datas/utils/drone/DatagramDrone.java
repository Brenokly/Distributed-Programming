package com.climate.datas.utils.drone;

import com.climate.datas.utils.JsonSerializable;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DatagramDrone implements JsonSerializable {
    private String droneId;
    private String data;

    public DatagramDrone(String droneId, String data) {
        this.droneId = droneId;
        this.data = data;
    }
}
