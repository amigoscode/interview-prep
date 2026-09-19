package com.amigoscode.lb;

public class NoInstancesException extends RuntimeException {
    public NoInstancesException() {
        super("no instances registered");
    }
}
