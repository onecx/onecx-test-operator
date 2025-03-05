package org.tkit.onecx.test.operator.rs.v1.controllers;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.test.operator.AbstractTest;

import io.quarkus.test.junit.QuarkusTest;

/**
 * This test is required to initialize standard tests from libraries
 */
@QuarkusTest
class BaseFirstTest extends AbstractTest {

    @Test
    void testQuarkusCloud() {
        given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/q/health")
                .then()
                .statusCode(200);
    }
}
