package org.tkit.onecx.test.domain.models;

import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(Throwable t) {
        super(t);
    }

}
