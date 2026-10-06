package com.usermanagement.common.web;

/** Simple acknowledgement body for endpoints that have nothing else to return. */
public record MessageResponse(String message) {
}
