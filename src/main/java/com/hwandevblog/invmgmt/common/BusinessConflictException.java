package com.hwandevblog.invmgmt.common;

public class BusinessConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessConflictException(String message) {
        super(message);
    }
}
