package org.tkit.onecx.test.domain.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TestExecution {

    private String path;
    private String proxy;
    private String url;
    private Integer code;
    private Status status;
    private String detailedStatus;
    private TestType testType;

    public enum Status {

        OK,

        //example: expected 401 response got 403
        WARNING,

        //unable to invoke call (unknowhostexcpetion, timeout and similar)
        ERROR,

        FAILED
    }

    public enum TestType {

        BFF_Q_ENDPOINTS_VIA_UI_PROXY,
        BFF_API_ENDPOINTS_VIA_UI_PROXY_NO_ID_TOKEN,
        BFF_API_ENDPOINTS_VIA_UI_PROXY_NO_ACCESS_TOKEN,
        BFF_API_ENDPOINTS_VIA_UI_PROXY_NO_PARTY_ID,
        BFF_API_ENDPOINTS_VIA_UI_PROXY_INVALID_ACCESS_TOKEN,
        BFF_API_ENDPOINTS_VIA_UI_PROXY_INVALID_ID_TOKEN,
        BFF_API_ENDPOINTS_VIA_UI_PROXY_VALID_TOKENS_AND_PARTY_ID;

    }

}
