package org.tkit.onecx.test.operator.rs.v1.mappers;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.RestResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.tkit.onecx.test.domain.models.ServiceException;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import gen.org.tkit.onecx.permission.model.ProblemDetailResponse;
import gen.org.tkit.onecx.test.operator.rs.v1.model.ProblemDetailInvalidParamDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.ProblemDetailParamDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.ProblemDetailResponseDTO;

@Mapper(uses = { OffsetDateTimeMapper.class })
public interface ExceptionMapper {

    default RestResponse<ProblemDetailResponseDTO> service(ServiceException ex) {
        var dto = exception(ErrorCodes.SERVICE_ERROR.name(), ex.getMessage());
        return RestResponse.status(Response.Status.BAD_REQUEST, dto);
    }

    default List<ProblemDetailInvalidParamDTO> createErrorValidationResponse(Map<String, String> problems) {
        if (problems == null) {
            return null;
        }
        return problems.entrySet().stream().map(k -> param(k.getKey(), k.getValue())).toList();
    }

    ProblemDetailInvalidParamDTO param(String name, String message);

    default RestResponse<ProblemDetailResponseDTO> constraint(ConstraintViolationException ex) {
        var dto = exception(ErrorCodes.CONSTRAINT_VIOLATIONS.name(), ex.getMessage());
        dto.setInvalidParams(createErrorValidationResponse(ex.getConstraintViolations()));
        return RestResponse.status(Response.Status.BAD_REQUEST, dto);
    }

    default Response clientException(ClientWebApplicationException ex) {
        if (ex.getResponse().getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        } else {
            if (ex.getResponse().getMediaType() != null
                    && ex.getResponse().getMediaType().toString().contains(APPLICATION_JSON)) {
                return Response.status(ex.getResponse().getStatus())
                        .entity(map(ex.getResponse().readEntity(ProblemDetailResponse.class))).build();
            } else {
                return Response.status(ex.getResponse().getStatus()).build();
            }
        }
    }

    @Mapping(target = "removeParamsItem", ignore = true)
    @Mapping(target = "removeInvalidParamsItem", ignore = true)
    ProblemDetailResponseDTO map(ProblemDetailResponse problemDetailResponse);

    @Mapping(target = "removeParamsItem", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "invalidParams", ignore = true)
    @Mapping(target = "removeInvalidParamsItem", ignore = true)
    ProblemDetailResponseDTO exception(String errorCode, String detail);

    default List<ProblemDetailParamDTO> map(Map<String, Object> params) {
        if (params == null) {
            return List.of();
        }
        return params.entrySet().stream().map(e -> {
            var item = new ProblemDetailParamDTO();
            item.setKey(e.getKey());
            if (e.getValue() != null) {
                item.setValue(e.getValue().toString());
            }
            return item;
        }).toList();
    }

    List<ProblemDetailInvalidParamDTO> createErrorValidationResponse(
            Set<ConstraintViolation<?>> constraintViolation);

    @Mapping(target = "name", source = "propertyPath")
    @Mapping(target = "message", source = "message")
    ProblemDetailInvalidParamDTO createError(ConstraintViolation<?> constraintViolation);

    default String mapPath(Path path) {
        return path.toString();
    }

    enum ErrorCodes {

        SERVICE_ERROR,
        CONSTRAINT_VIOLATIONS
    }
}
