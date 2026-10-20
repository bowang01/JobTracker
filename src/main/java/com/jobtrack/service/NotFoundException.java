package com.jobtrack.service;

/**
 * The resource does not exist, or the current user may not access it. Both return 404 so IDs cannot be probed.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found or access denied");
    }
}
