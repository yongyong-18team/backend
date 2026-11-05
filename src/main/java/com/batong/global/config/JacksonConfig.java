package com.batong.global.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** API 명세: 시각은 ISO-8601(+09:00). Jackson 기본은 Instant를 UTC(Z)로 쓰므로 KST 오프셋으로 바꾼다. */
@Configuration
public class JacksonConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(KST);

    @Bean
    Jackson2ObjectMapperBuilderCustomizer instantAsKst() {
        return builder -> builder.serializerByType(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(FORMAT.format(value));
            }
        });
    }
}
