package com.climate.datas.utils.drone;

import com.climate.datas.utils.JsonSerializable;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.DatagramPacket;

@Data
@NoArgsConstructor
public class DatagramDrone implements JsonSerializable {
    private DroneId droneId;
    private String data;

    public DatagramDrone(DroneId droneId, String data) {
        this.droneId = droneId;
        this.data = data;
    }

    public byte[] toBytes() {
        return this.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static DatagramDrone fromBytes(byte[] bytes, int length) {
        String json = new String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8);
        return JsonSerializable.fromJson(json, DatagramDrone.class);
    }

    public static DatagramDrone fromPacket(DatagramPacket packet) {
        return fromBytes(packet.getData(), packet.getLength());
    }

    @Override
    public String toString() {
        return "Drone ID: " + droneId + ", Dados: " + data;
    }
}
