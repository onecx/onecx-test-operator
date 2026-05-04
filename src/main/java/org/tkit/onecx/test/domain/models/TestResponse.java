package org.tkit.onecx.test.domain.models;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TestResponse {

    private String id;

    private String url;

    private String service;

    private Status status;

    private String detailedStatus;

    private List<TestExecution> executions;

    private List<String> whitelistedPaths;

    public enum Status {

        OK,

        FAILED,

        ERROR,

        WARNING
    }

    public String getDetailedStatus() {
        if (detailedStatus != null) {
            return detailedStatus;
        }
        return switch (status) {
            case FAILED -> "At least 1 of tested resourced failed penetration test. See execution details for more info.";
            case ERROR -> "Invokation of test failed on at least 1 resource. See execution details for more info";
            case WARNING ->
                "At least 1 of tested resourced returned unexpected response code. See execution details for more info.";
            case OK -> "All tested resources passed penetration test.";
        };
    }
}
