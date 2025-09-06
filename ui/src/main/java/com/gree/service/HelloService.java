package com.gree.service;

import org.springframework.stereotype.Service;

@Service
public class HelloService {
    public String getMessage() {
        return "Hello from Spring Boot + JavaFX!";
    }
}
