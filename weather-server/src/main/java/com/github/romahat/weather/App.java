package com.github.romahat.weather;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class App extends Application<AppConfiguration>{
    public static void main(String[] args) throws Exception {
        new App().run("server");
    }

    @Override
    public String getName() {
        return "weather-app";
    }

    @Override
    public void initialize(Bootstrap<AppConfiguration> bootstrap) {
        super.initialize(bootstrap);
    }

    public void run(AppConfiguration configuration, Environment environment) {
        environment.lifecycle().manage(new WeatherServer(50051));
    }
}
