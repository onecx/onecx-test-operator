package org.tkit.onecx.test.domain.models;

import java.util.List;
import java.util.Map;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "onecx.test.scheduler")
public interface TestRunConfig {

    /**
     * Cron expression for the scheduler trigger.
     */
    String cron();

    /**
     * Configured test environments with URL and services keyed by environment name.
     */
    Map<String, UrlServices> services();

    interface UrlServices {

        /**
         * Base URL of the target environment.
         */
        String url();

        /**
         * List of enabled service identifiers.
         */
        List<String> services();
    }
}
