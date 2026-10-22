package com.gymflow.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj único de la aplicación: la zona horaria del gimnasio define qué es "hoy" y "este mes". */
@Configuration
class TimeConfig {

    @Bean
    Clock clock(@Value("${gymflow.timezone:}") String zone) {
        return zone.isBlank() ? Clock.systemDefaultZone() : Clock.system(ZoneId.of(zone));
    }
}
