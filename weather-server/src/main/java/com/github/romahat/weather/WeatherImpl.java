package com.github.romahat.weather;

import com.github.romahat.grpc.WeatherGrpc;
import com.github.romahat.grpc.WeatherReply;
import com.github.romahat.grpc.WeatherRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class WeatherImpl extends WeatherGrpc.WeatherImplBase {
    private final static Logger LOGGER = LoggerFactory.getLogger(WeatherImpl.class);
    private final Random random = new Random();

    @Override
    public void getWeather(WeatherRequest request, io.grpc.stub.StreamObserver<WeatherReply> responseObserver) {
        //LOGGER.info("Obtained query: {}", request.getCity());
        String temperature = Integer.toString(random.nextInt(30));
        WeatherReply reply = WeatherReply.newBuilder()
                .setTemperature(temperature)
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
        //LOGGER.info("Submitted value: {}", temperature);
    }
}
