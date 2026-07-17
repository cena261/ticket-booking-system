package com.ticketapp.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackJsonEncoderTest {

    @Test
    void encodesValidJsonWithCustomFields() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.setCustomFields("{\"app\":\"ticket-app\",\"instance\":\"app-1\"}");
        encoder.start();

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(sink);
        appender.start();

        Logger logger = context.getLogger("com.ticketapp.jsonformat");
        logger.setAdditive(false);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        logger.info("reserve completed");

        appender.stop();
        JsonNode json = new ObjectMapper().readTree(sink.toByteArray());

        assertThat(json.get("message").asText()).isEqualTo("reserve completed");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("app").asText()).isEqualTo("ticket-app");
        assertThat(json.get("instance").asText()).isEqualTo("app-1");
        assertThat(json.hasNonNull("@timestamp")).isTrue();
    }
}
