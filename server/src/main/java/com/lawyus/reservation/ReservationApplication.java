package com.lawyus.reservation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 轻量美业预约小程序后端启动类。
 * 包结构遵循技术方案：kernel（预约内核）/ beauty（美业皮肤）/ integration（微信与文件）/ job（定时任务）。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.lawyus.reservation.**.mapper")
public class ReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationApplication.class, args);
    }
}