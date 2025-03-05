package org.tkit.onecx.test.operator.rs.v1.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import gen.org.tkit.onecx.test.operator.rs.v1.model.ExecutionStatusDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestExecutionDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestRequestDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestResponseDTO;

@Mapper
public interface TestMapper {

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "executions", ignore = true)
    @Mapping(target = "removeExecutionsItem", ignore = true)
    SecurityTestResponseDTO response(SecurityTestRequestDTO dto);

    default String url(SecurityTestRequestDTO dto) {
        var tmp = dto.getUrl();
        if (tmp.endsWith("/")) {
            tmp = tmp.substring(0, tmp.length() - 1);
        }
        return tmp;
    }

    default SecurityTestExecutionDTO createExecutionError(String path, String proxy, String error) {
        return createExecution(path, proxy, ExecutionStatusDTO.ERROR, null, null).error(error);
    }

    @Mapping(target = "error", ignore = true)
    SecurityTestExecutionDTO createExecution(String path, String proxy, ExecutionStatusDTO status, String url,
            Integer code);
}
