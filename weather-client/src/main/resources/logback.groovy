appender("STDOUT", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{dd-HH:mm:ss.SSS}:%-5level[%thread]: %logger{36} - %msg%n"
    }
}

logger("com.foo", DEBUG, ["CONSOLE"])

root(INFO, ["STDOUT"])