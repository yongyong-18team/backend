package com.batong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.TimeZone;

// JWT로만 인증하므로 Spring Security 기본 사용자(콘솔에 생성 비밀번호 출력)를 만들지 않는다
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BatongApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(BatongApplication.class, args);
    }
}
