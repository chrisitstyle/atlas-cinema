package com.atlascinema.booking.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/clear-database.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public abstract class AbstractIntegrationTest extends BaseTestEnvironment {
}
