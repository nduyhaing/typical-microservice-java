package com.shopapp.commonlibrary.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.commonlibrary.constants.MessageCode;
import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import org.junit.jupiter.api.Test;

class ExceptionMessageTest {

    @Test
    void notFoundResolvesMessage() {
        assertThat(new NotFoundException("RESOURCE_NOT_FOUND", "Product", 7).getMessage())
            .isEqualTo("Resource Product with id 7 not found");
    }

    @Test
    void accessDeniedResolvesMessageCode() {
        assertThat(new AccessDeniedException(MessageCode.ACCESS_DENIED).getMessage()).isEqualTo("Access denied");
    }

    @Test
    void internalServerErrorKeepsRawMessage() {
        assertThat(new InternalServerErrorException("boom").getMessage()).isEqualTo("boom");
    }

    @Test
    void errorVmShortConstructorHasEmptyFieldErrors() {
        assertThat(new ErrorVm("404 NOT_FOUND", "Not Found", "x").fieldErrors()).isEmpty();
    }
}
