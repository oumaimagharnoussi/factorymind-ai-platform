package com.factorymind.maintenance.common;

import java.util.UUID;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, UUID id) {
        super(resource + " " + id + " was not found");
    }
}
