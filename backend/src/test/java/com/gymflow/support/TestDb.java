package com.gymflow.support;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class TestDb {

    private final JdbcClient jdbc;

    TestDb(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Vacía los datos de negocio (se conservan los usuarios) para aislar cada test. */
    public void reset() {
        jdbc.sql("TRUNCATE check_ins, payments, memberships, plans, members RESTART IDENTITY CASCADE").update();
    }
}
