package com.climate.datas.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServerInfo {
    private String host;
    private int port;
}
