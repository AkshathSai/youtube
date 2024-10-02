package org.revamped.youtube.resilience;

@FunctionalInterface
public interface RetryExecutor<T> {

    T run() throws Exception;

}