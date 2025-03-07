package org.tkit.onecx.test.operator.ui.controllers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.tkit.onecx.test.domain.models.ServiceException;
import org.tkit.onecx.test.domain.services.TestService;
import org.tkit.onecx.test.operator.ui.mappers.UIExceptionMapper;
import org.tkit.onecx.test.operator.ui.mappers.UIMapper;

import gen.org.tkit.onecx.test.operator.ui.UiApiService;
import gen.org.tkit.onecx.test.operator.ui.model.ProblemDetailResponseDTO;
import gen.org.tkit.onecx.test.operator.ui.model.TestRequestDTO;

@ApplicationScoped
public class UIController implements UiApiService {

    @Inject
    TestService testService;

    @Inject
    UIMapper mapper;

    @Inject
    UIExceptionMapper exceptionMapper;

    @Override
    public Response executeTest(TestRequestDTO testRequestDTO) {
        var req = mapper.map(testRequestDTO);
        var data = testService.execute(req);
        return Response.ok(mapper.create(data)).build();
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
