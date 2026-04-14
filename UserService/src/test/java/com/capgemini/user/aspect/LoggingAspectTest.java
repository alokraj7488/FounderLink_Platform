package com.capgemini.user.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    @Mock
    private Signature signature;

    @InjectMocks
    private LoggingAspect loggingAspect;

    @Test
    void logServiceMethods_whenMethodSucceeds_shouldLogAndReturn() throws Throwable {
        // given
        when(proceedingJoinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("UserService.getProfile");
        when(proceedingJoinPoint.proceed()).thenReturn("result");

        // when
        Object result = loggingAspect.logServiceMethods(proceedingJoinPoint);

        // then
        assertThat(result).isEqualTo("result");
        verify(proceedingJoinPoint).proceed();
    }

    @Test
    void logServiceMethods_whenMethodFails_shouldLogAndThrow() throws Throwable {
        // given
        when(proceedingJoinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("UserService.getProfile");
        RuntimeException exception = new RuntimeException("Test Error");
        when(proceedingJoinPoint.proceed()).thenThrow(exception);

        // when / then
        assertThatThrownBy(() -> loggingAspect.logServiceMethods(proceedingJoinPoint))
                .isEqualTo(exception);
        verify(proceedingJoinPoint).proceed();
    }
}
