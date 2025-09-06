package com.gree;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import javafx.application.Application;

@SpringBootApplication
public class MyApp {
    public static void main(String[] args) {
        Application.launch(JavaFxApp.class, args);
    }
}
