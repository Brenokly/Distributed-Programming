package com.climate.datas.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Loggable {
    default Logger logger() {
        return LoggerFactory.getLogger(this.getClass().getName());
    }

    default void info(String message) {
        System.out.println("\n" + message);
        logger().info(message);
        System.out.println("--------------------------------------------------------------");
    }

    default void message(String message) {
        logger().info(message);
    }

    default void erro(String message) {
        System.out.println("\n" + message);
        logger().error(message);
        System.out.println("--------------------------------------------------------------");
    }

    default void debug(String message) {
        System.out.println("\n" + message);
        logger().debug(message);
        System.out.println("--------------------------------------------------------------");
    }

    default void warn(String message) {
        System.out.println("\n" + message);
        logger().warn(message);
        System.out.println("--------------------------------------------------------------");
    }
}