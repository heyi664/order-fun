package com.heyee.comments;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.heyee.comments.mapper")
@SpringBootApplication
public class HeyeeCommentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeyeeCommentsApplication.class, args);
    }

}
