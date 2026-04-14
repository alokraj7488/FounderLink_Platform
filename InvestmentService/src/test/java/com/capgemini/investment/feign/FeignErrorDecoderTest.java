package com.capgemini.investment.feign;

import com.capgemini.investment.exception.ResourceNotFoundException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    @Test
    void decode_when404_shouldReturnResourceNotFoundException() {
        // given
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(Request.create(Request.HttpMethod.GET, "/url", Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
                .build();

        // when
        Exception exception = decoder.decode("methodKey", response);

        // then
        assertThat(exception).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Requested resource not found");
    }

    @Test
    void decode_when500_shouldReturnDefaultException() {
        // given
        Response response = Response.builder()
                .status(500)
                .reason("Internal Server Error")
                .request(Request.create(Request.HttpMethod.GET, "/url", Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
                .build();

        // when
        Exception exception = decoder.decode("methodKey", response);

        // then
        assertThat(exception).isNotNull().isNotInstanceOf(ResourceNotFoundException.class);
    }
}
