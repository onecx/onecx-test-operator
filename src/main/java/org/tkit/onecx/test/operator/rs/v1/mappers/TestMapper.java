package org.tkit.onecx.test.operator.rs.v1.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.tkit.onecx.test.domain.models.TestRequest;
import org.tkit.onecx.test.domain.models.TestResponse;

import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestRequestDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestResponseDTO;

@Mapper
public interface TestMapper {

    TestRequest map(SecurityTestRequestDTO dto);

    @Mapping(target = "removeExecutionsItem", ignore = true)
    SecurityTestResponseDTO create(TestResponse data);
}
