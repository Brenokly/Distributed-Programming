package com.climate.datas.utils.user;

import com.climate.datas.utils.JsonSerializable;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserResponse implements JsonSerializable {

    int id;
    UserResponseEnum response;

    public UserResponse(int id, UserResponseEnum userResponseEnum) {
        this.id = id;
        this.response = userResponseEnum;
    }

}
