package com.heyee.comments;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.heyee.comments.mapper")
@EnableScheduling
@SpringBootApplication
public class HeyeeCommentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeyeeCommentsApplication.class, args);
    }

}
