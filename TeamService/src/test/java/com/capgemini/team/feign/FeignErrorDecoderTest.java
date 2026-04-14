package com.capgemini.team.feign;

import com.capgemini.team.exception.ResourceNotFoundException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class FeignErrorDecoderTest {

    private FeignErrorDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new FeignErrorDecoder();
    }

    private Response buildResponse(int status) {
        return Response.builder()
                .status(status)
                .reason("reason")
                .request(Request.create(Request.HttpMethod.GET, "/test",
                        Collections.emptyMap(), null, null, null))
                .headers(Collections.emptyMap())
                .build();
    }

    @Test
    void decode_with404_shouldReturnResourceNotFoundException() {
        Response response = buildResponse(404);

        Exception ex = decoder.decode("StartupClient#getStartupById(Long)", response);

        assertThat(ex).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void decode_with500_shouldDelegateToDefaultDecoder() {
        Response response = buildResponse(500);

        Exception ex = decoder.decode("StartupClient#getStartupById(Long)", response);

        assertThat(ex).isNotInstanceOf(ResourceNotFoundException.class);
    }
}
