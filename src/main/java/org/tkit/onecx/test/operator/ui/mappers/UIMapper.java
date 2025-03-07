package org.tkit.onecx.test.operator.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.tkit.onecx.test.domain.models.TestRequest;
import org.tkit.onecx.test.domain.models.TestResponse;

import gen.org.tkit.onecx.test.operator.ui.model.TestRequestDTO;
import gen.org.tkit.onecx.test.operator.ui.model.TestResponseDTO;

@Mapper
public interface UIMapper {

    TestRequest map(TestRequestDTO dto);

    @Mapping(target = "removeExecutionsItem", ignore = true)
    TestResponseDTO create(TestResponse data);
}
