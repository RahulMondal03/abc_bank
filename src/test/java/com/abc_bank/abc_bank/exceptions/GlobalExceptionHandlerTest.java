package com.abc_bank.abc_bank.exceptions;

import com.abc_bank.abc_bank.res.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAllUnknownExceptions_returns500WithMessage() {
        ResponseEntity<Response<?>> response =
                handler.handleAllUnknownExceptions(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(response.getBody().getMessage()).isEqualTo("boom");
    }

    @Test
    void handleNotFoundExceptions_returns404WithMessage() {
        ResponseEntity<Response<?>> response =
                handler.handleNotFoundExceptions(new NotFoundException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getBody().getMessage()).isEqualTo("nope");
    }

    @Test
    void handleInsufficientBalance_returns400WithMessage() {
        ResponseEntity<Response<?>> response =
                handler.handleInsufficientBalance(new InsufficientBalanceException("low funds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody().getMessage()).isEqualTo("low funds");
    }

    @Test
    void handleInvalidTransaction_returns400WithMessage() {
        ResponseEntity<Response<?>> response =
                handler.handleInvalidTransaction(new InvalidTransactionException("invalid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody().getMessage()).isEqualTo("invalid");
    }

    @Test
    void handleBadRequestException_returns400WithMessage() {
        ResponseEntity<Response<?>> response =
                handler.handleBadRequestException(new BadRequestException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody().getMessage()).isEqualTo("bad");
    }
}
