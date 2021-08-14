package com.github.romahat.weather;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.dropwizard.lifecycle.Managed;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WeatherServer implements Managed {
    private final static Logger LOGGER = LoggerFactory.getLogger(WeatherServer.class);

    private final int port;
    private final Server server;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final LinkedBlockingQueue<Runnable> queue;

    public WeatherServer(int port) {
        this.port = port;
        this.queue = new LinkedBlockingQueue<>();
        this.threadPoolExecutor = getThreadPoolExecutor(queue);
        this.server = ServerBuilder.forPort(port)
                .executor(threadPoolExecutor)
                .addService(new WeatherImpl())
                .build();
    }

    private ThreadPoolExecutor getThreadPoolExecutor(LinkedBlockingQueue<Runnable> queue) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10, 10,
                0L, TimeUnit.MILLISECONDS,
                queue,
                new ThreadFactoryBuilder()
                        .setNameFormat("grpc-incoming-%d")
                        .build());
        threadPoolExecutor.prestartAllCoreThreads();
        return threadPoolExecutor;
    }

    @Override
    public void start() throws Exception {
        server.start();
        LOGGER.info("Server started on port: {}", port);
        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(this::monitor, 300, 600, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        server.shutdown();
        threadPoolExecutor.shutdown();
        LOGGER.info("Shutting down service...");
    }

    private void monitor() {
        System.out.println("#### Thread Report:: Active:" + threadPoolExecutor.getActiveCount() + " Pool: "
                + threadPoolExecutor.getPoolSize() + " MaxPool: " + threadPoolExecutor.getMaximumPoolSize()
                + " ####" + "Queue size: " + queue.size());
    }
}
