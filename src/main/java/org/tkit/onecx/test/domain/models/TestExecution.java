package org.tkit.onecx.test.domain.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestExecution {

    private String path;
    private String proxy;
    private String url;
    private Integer code;
    private Status status;
    private String error;

    public enum Status {

        OK,

        FAILED,

        ERROR;
    }

}
