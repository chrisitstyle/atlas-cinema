package com.atlascinema.payment.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/*
 * Loads the full payment-service application context
 * Cleans payment data after each integration test
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/clear-database.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public abstract class AbstractIntegrationTest extends BaseTestEnvironment {
}
