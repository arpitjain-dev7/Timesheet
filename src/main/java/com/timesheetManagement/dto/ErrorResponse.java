package com.timesheetManagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)   // omit null fields from JSON
public class ErrorResponse {

    private String        errorId;       // UUID for log correlation
    private LocalDateTime timestamp;
    private int           status;
    private String        error;         // HTTP reason phrase
    private String        message;       // human-readable description
    private String        path;          // request URI
    private Object        details;       // validation field errors, etc.
}

