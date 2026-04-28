package org.tkit.onecx.test.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class TestServiceUriUtilTest {

    @Test
    void normalizeUri_returnsNullForNullInput() {
        assertThat(TestServiceUriUtil.normalizeUri(null)).isNull();
    }

    @Test
    void normalizeUri_returnsNullForEmptyInput() {
        assertThat(TestServiceUriUtil.normalizeUri("")).isEmpty();
    }

    @Test
    void normalizeUri_returnsEmptyInputUnchanged() {
        String input = "";

        assertThat(TestServiceUriUtil.normalizeUri(input)).isEqualTo(input);
    }

    @Test
    void normalizeUri_returnsBlankInputUnchanged() {
        String input = "   ";

        assertThat(TestServiceUriUtil.normalizeUri(input)).isEqualTo(input);
    }

    @Test
    void normalizeUri_preservesSchemeAndNormalizesSlashesAndPlaceholders() {
        String input = "https://host//api///users/{userId}//orders/{id}";

        assertThat(TestServiceUriUtil.normalizeUri(input))
                .isEqualTo("https://host/api/users/1234/orders/1234");
    }

    @Test
    void normalizeUri_normalizesSlashesWithoutScheme() {
        String input = "/api//v1///resource";

        assertThat(TestServiceUriUtil.normalizeUri(input)).isEqualTo("/api/v1/resource");
    }

    @Test
    void normalizeUri_normalizesSlashesWithoutSchemeAndReplacesPlaceholders() {
        String input = "api//v1///users/{userId}";

        assertThat(TestServiceUriUtil.normalizeUri(input)).isEqualTo("api/v1/users/1234");
    }

    @Test
    void findOverlapLength_returnsLengthWhenOverlapExists() {
        assertThat(TestServiceUriUtil.findOverlapLength("/api/service", "/service/users")).isEqualTo(8);
    }

    @Test
    void findOverlapLength_returnsZeroWhenNoOverlapExists() {
        assertThat(TestServiceUriUtil.findOverlapLength("/api", "/other/path")).isZero();
    }

    @Test
    void findOverlapLength_returnsZeroWhenFirstStringIsEmpty() {
        assertThat(TestServiceUriUtil.findOverlapLength("", "/any/path")).isZero();
    }
}
