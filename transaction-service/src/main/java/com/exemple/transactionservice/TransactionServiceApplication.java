package com.exemple.transactionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

import java.util.Date;
import java.util.List;

@SpringBootApplication
@EnableCaching  // Active le cache Spring
public class TransactionServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
