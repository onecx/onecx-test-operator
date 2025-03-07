package org.tkit.onecx.test.operator.rs.v1.controllers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.tkit.onecx.test.domain.models.ServiceException;
import org.tkit.onecx.test.domain.services.*;
import org.tkit.onecx.test.operator.rs.v1.mappers.ExceptionMapper;
import org.tkit.onecx.test.operator.rs.v1.mappers.TestMapper;

import gen.org.tkit.onecx.test.operator.rs.v1.TestApiService;
import gen.org.tkit.onecx.test.operator.rs.v1.model.*;

@ApplicationScoped
@Transactional(value = Transactional.TxType.NOT_SUPPORTED)
public class TestRestController implements TestApiService {

    @Inject
    TestService testService;

    @Inject
    ExceptionMapper exceptionMapper;

    @Inject
    TestMapper testMapper;

    @Override
    public Response executeSecurityTest(SecurityTestRequestDTO dto) {
        var req = testMapper.map(dto);
        var response = testService.execute(req);
        return Response.ok(testMapper.create(response)).build();
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> constraint(ConstraintViolationException ex) {
        return exceptionMapper.constraint(ex);
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> serviceException(ServiceException ex) {
        return exceptionMapper.service(ex);
    }
}
