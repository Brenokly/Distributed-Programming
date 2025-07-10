package com.climate.datas.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerInfo implements JsonSerializable {

    private String host;
    private int port;
}
