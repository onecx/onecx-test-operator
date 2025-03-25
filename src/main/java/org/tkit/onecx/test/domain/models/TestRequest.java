package org.tkit.onecx.test.domain.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestRequest {

    private String id;

    private String url;

    private String service;

    private boolean quarkus;
}
