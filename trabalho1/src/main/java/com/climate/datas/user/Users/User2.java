package com.climate.datas.user.Users;

import com.climate.datas.user.User;

public class User2 {
    public static void main(String[] args) {
        try (User user = new User(2)) {
            user.start();
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o usuário: " + e.getMessage());
        }
    }
}
