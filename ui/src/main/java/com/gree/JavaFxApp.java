package com.gree;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApp extends Application {
    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        context = new SpringApplicationBuilder(MyApp.class).run();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
        loader.setControllerFactory(context::getBean); // Spring-managed controllers
        primaryStage.setScene(new Scene(loader.load(), 600, 400));
        primaryStage.setTitle("Gree Airconditioner Desktop App (unofficial)");
        primaryStage.show();
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
