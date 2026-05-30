package com.airasia.flight.calendar;

import com.airasia.flight.currency.UnsupportedCurrencyException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({UnsupportedCurrencyException.class, IllegalArgumentException.class, ConstraintViolationException.class, MethodArgumentNotValidException.class, MissingServletRequestParameterException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
