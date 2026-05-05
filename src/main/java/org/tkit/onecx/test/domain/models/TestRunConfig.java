package org.tkit.onecx.test.domain.models;

import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "onecx.test.scheduler")
public interface TestRunConfig {

    /**
     * Cron expression for the scheduler trigger.
     */
    String timer();

    /**
     * Configuration of services used for private test runs.
     */
    UrlServices privateRun();

    /**
     * Configuration of services used for public test runs.
     */
    UrlServices publicRun();

    interface UrlServices {

        /**
         * Base URL of the target environment.
         */
        Optional<String> url();

        /**
         * List of enabled service identifiers.
         */
        Optional<List<String>> services();
    }
}
