package com.Ecom.SpringEcom.models.dtos;

import java.time.LocalDateTime;

public record ErrorResponse(int status,
                            String message,
                            String error,
                            String path,
                            LocalDateTime timestamp) {
}
