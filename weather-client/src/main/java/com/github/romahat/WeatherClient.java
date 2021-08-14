package com.github.romahat;

import com.github.romahat.grpc.City;
import com.github.romahat.grpc.WeatherGrpc;
import com.github.romahat.grpc.WeatherReply;
import com.github.romahat.grpc.WeatherRequest;
import com.google.common.util.concurrent.*;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WeatherClient {
    private final static Logger LOGGER = LoggerFactory.getLogger(WeatherClient.class);
    private final ManagedChannel channel;
    private final WeatherGrpc.WeatherBlockingStub blockingStub;
    private final WeatherGrpc.WeatherStub asyncStub;
    private final WeatherGrpc.WeatherFutureStub futureStub;

    public WeatherClient() {
        channel = ManagedChannelBuilder
                .forAddress("127.0.0.1", 50051)
                .usePlaintext()
                .build();
        blockingStub = WeatherGrpc.newBlockingStub(channel);
        asyncStub = WeatherGrpc.newStub(channel);
        futureStub = WeatherGrpc.newFutureStub(channel);
    }

    public static void main(String[] args) throws InterruptedException {
        LOGGER.info("Running weather requests...");
        ExecutorService executorService = Executors.newFixedThreadPool(50, new ThreadFactoryBuilder()
                .setNameFormat("running-queries-%d")
                .build());
        ExecutorService callBacksExecutor = Executors.newFixedThreadPool(50, new ThreadFactoryBuilder()
                .setNameFormat("callback-queries-%d")
                .build());
        WeatherClient weatherClient = new WeatherClient();
        submitBlockingCallsInExecutor(weatherClient, executorService);
        submitFuturesCalls(callBacksExecutor, weatherClient);
        shutdown(executorService, callBacksExecutor);
        //weatherClient.shutdown();
    }

    private static void submitFuturesCalls(ExecutorService executorService, WeatherClient weatherClient) {
        Random random = new Random();
        for (int i=0; i< 1_000_000; i++) {
            WeatherRequest request = WeatherRequest.newBuilder()
                    .setCity(City.forNumber(random.nextInt(16)))
                    .build();
            ListenableFuture<WeatherReply> weather = weatherClient.futureStub.getWeather(request);
            Futures.addCallback(weather,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(@NullableDecl WeatherReply result) {
                            LOGGER.info("From future: " + result.getTemperature());
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            LOGGER.error("Error in future call: {}", t.getMessage(), t);
                        }
                    },
                    executorService);
        }
    }

    private static void submitBlockingCallsInExecutor(WeatherClient weatherClient, ExecutorService executorService) {
        Random random = new Random();
        for (int i=1; i<1_000_000; i++) {
            executorService.submit(() -> {
                try {
                    WeatherRequest request = WeatherRequest.newBuilder()
                            .setCity(City.forNumber(random.nextInt(16)))
                            .build();
                    WeatherReply weather = weatherClient.blockingStub
                            .withDeadline(Deadline.after(10, TimeUnit.SECONDS))
                            .getWeather(request);
                    LOGGER.info("Weather in Odessa: " + weather.getTemperature());
                } catch (Exception e) {
                    LOGGER.error("Error in blocking call", e);
                }
            });
        }
    }

    private static void shutdown(ExecutorService executorService, ExecutorService callbacks) throws InterruptedException {
        executorService.shutdown();
        callbacks.shutdown();
        while (!executorService.isTerminated() && !callbacks.isTerminated()) {
            if (System.currentTimeMillis() % 10 ==0) {
                LOGGER.info("Terminating");
                TimeUnit.SECONDS.sleep(1000);
            }
        }
    }

    public void shutdown() throws InterruptedException {
        channel.shutdownNow();
        int retryCount = 0;
        while (!channel.isTerminated() && retryCount < 5) {
            channel.awaitTermination(1, TimeUnit.SECONDS);
            retryCount++;
        }
        if (!channel.isTerminated()) throw new IllegalStateException("Channel not closed!");
    }
}
