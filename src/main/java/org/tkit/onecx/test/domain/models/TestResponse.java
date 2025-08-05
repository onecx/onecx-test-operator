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

    private List<TestExecution> executions;

    private List<String> whitelistedPaths;

    public enum Status {

        OK,

        FAILED,

        ERROR;
    }
}
