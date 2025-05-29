package com.climate.datas.utils.user;

import com.climate.datas.utils.JsonSerializable;
import lombok.Getter;

@Getter
public enum UserResponseEnum implements JsonSerializable {
    HASHING(0), ROUND_ROBIN(1);

    private final int value;

    UserResponseEnum(int value) {
        this.value = value;
    }

    public static UserResponseEnum fromValue(int value) {
        for (UserResponseEnum response : UserResponseEnum.values()) {
            if (response.getValue() == value) {
                return response;
            }
        }
        return null;
    }
}
