package com.wedding.packages;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.wedding")
@MapperScan("com.wedding.packages.mapper")
public class PackageApplication {
    public static void main(String[] args) {
        SpringApplication.run(PackageApplication.class, args);
    }
}
