package com.gymflow;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.springframework.boot.SpringApplication;

/**
 * Arranque local sin Docker ni PostgreSQL instalado: levanta un PostgreSQL embebido en el puerto 54329
 * (datos en target/dev-pg, persisten entre ejecuciones) y la API (puerto 8081) con el perfil "demo".
 * Uso: ./mvnw spring-boot:test-run
 */
public class DevGymflowApplication {

    public static void main(String[] args) throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setPort(54329)
                .setDataDirectory(java.nio.file.Path.of("target", "dev-pg"))
                .setCleanDataDirectory(false)
                .start();
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        System.setProperty("server.port", "8081"); // 8080 suele estar ocupado en equipos de desarrollo
        System.setProperty("spring.profiles.active", "demo");
        System.setProperty("gymflow.security.jwt-secret", "clave-solo-para-desarrollo-local-0123456789");
        SpringApplication.run(GymflowApiApplication.class, args);
    }
}
