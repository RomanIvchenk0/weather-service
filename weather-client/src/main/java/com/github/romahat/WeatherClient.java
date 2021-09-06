package com.github.romahat;

import com.github.romahat.grpc.City;
import com.github.romahat.grpc.WeatherGrpc;
import com.github.romahat.grpc.WeatherReply;
import com.github.romahat.grpc.WeatherRequest;
import com.google.common.util.concurrent.*;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.util.concurrent.SingleThreadEventExecutor;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class WeatherClient {
    private final static Logger LOGGER = LoggerFactory.getLogger(WeatherClient.class);
    private final ManagedChannel channel;
    private final WeatherGrpc.WeatherBlockingStub blockingStub;
    private final WeatherGrpc.WeatherStub asyncStub;
    private final WeatherGrpc.WeatherFutureStub futureStub;
    private final AtomicLong successCounter = new AtomicLong();

    public WeatherClient() {
        channel = ManagedChannelBuilder
                .forAddress("192.168.0.105", 50051)
                .usePlaintext()
                .build();
        blockingStub = WeatherGrpc.newBlockingStub(channel);
        asyncStub = WeatherGrpc.newStub(channel);
        futureStub = WeatherGrpc.newFutureStub(channel);
    }

    public static void main(String[] args) throws InterruptedException {
        LOGGER.info("Running weather requests...");
        ThreadPoolExecutor callBacksExecutor = new ThreadPoolExecutor(10, 10,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder()
                        .setNameFormat("grpc-requests-%d")
                        .build());
        WeatherClient weatherClient = new WeatherClient();
        weatherClient.submitFuturesCalls(callBacksExecutor);
        while(!callBacksExecutor.isTerminated()) {
            LOGGER.info("#### Thread Report:: Active:" + callBacksExecutor.getActiveCount() + " Pool: "
                    + callBacksExecutor.getPoolSize() + " MaxPool: " + callBacksExecutor.getMaximumPoolSize()
                    + " ####\n" + "Queue size: " + callBacksExecutor.getQueue().size() + ", success: " + weatherClient.successCounter.get());
            EventLoopGroup.iterator();
            TimeUnit.SECONDS.sleep(5);
        }
    }

    private void submitFuturesCalls(ExecutorService executorService) {
        Random random = new Random();
        for (int i=0; i< 3_00_000; i++) {
            if (i % 10000 == 0) {
                LOGGER.info("Submitting {} requests", i);
            }
            WeatherRequest request = WeatherRequest.newBuilder()
                    .setCity(City.forNumber(random.nextInt(16)))
                    .build();
            ListenableFuture<WeatherReply> weather = this.futureStub
                    .withDeadline(Deadline.after(10,TimeUnit.SECONDS)).getWeather(request);
            Futures.addCallback(weather,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(@NullableDecl WeatherReply result) {
                            successCounter.incrementAndGet();
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            LOGGER.error("Error in future call: {}", t.getMessage(), t);
                        }
                    },
                    executorService);
        }
        LOGGER.info("Finished queries submitting");
    }

    private static void submitBlockingCallsInExecutor(WeatherClient weatherClient) {
        Random random = new Random();
        for (int i=1; i<1_000_000; i++) {
            try {
                City city = City.forNumber(random.nextInt(16));
                WeatherRequest request = WeatherRequest.newBuilder()
                        .setCity(city)
                        .build();
                WeatherReply weather = weatherClient.blockingStub
                        .withDeadline(Deadline.after(10, TimeUnit.SECONDS))
                        .getWeather(request);
                LOGGER.info("Weather in {}: {}", city, weather.getTemperature());
            } catch (Exception e) {
                LOGGER.error("Error in blocking call", e);
            }
        }
    }

    private static void shutdown(ExecutorService executorService) throws InterruptedException {
        executorService.shutdown();
        while (!executorService.isTerminated()) {
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
