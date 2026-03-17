package org.triple.backend.payment.entity.outbox;

public enum Error {
    NETWORK_TIMEOUT,
    UPSTREAM_429,
    UPSTREAM_5XX,
    UPSTREAM_4XX,
    UNKNOWN
    ;
}
