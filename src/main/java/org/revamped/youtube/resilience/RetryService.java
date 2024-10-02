package org.revamped.youtube.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RetryService<T> {
    private static final Logger log = LoggerFactory.getLogger(RetryService.class);

    //@Value("${retryAttempts}")
    private int retryAttempts = 4;
    //@Value("${timeToWait}")
    private final long timeToWait = TimeUnit.SECONDS.toSeconds(1000);

    public T retry(RetryExecutor<T> retryExecutor) {

        while (shouldRetry()) {
            try {
                //log.info("Retrying...");
                T result = retryExecutor.run();
                if (result!=null) {
                    return result;
                }
                //return; // if successful, exit method
            } catch (Exception e) {
                retryAttempts--;
                if (shouldRetry()) {
                    //log.error(e.getMessage(), e);
                    log.error(e.getMessage());
                    waitBeforeNextRetry();
                } else {
                    //throw e; // if all retries failed, throw the exception
                    log.error(e.getMessage(), e);
                }
            }
        }

        return null; // if all retries failed
    }

    private boolean shouldRetry() {
        return retryAttempts > 0;
    }

    private void waitBeforeNextRetry() {
        try {
            log.info("Waiting before next retry...");
            Thread.sleep(timeToWait);
        } catch (Exception e) {
            log.error("Exception while waiting for next retry {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

}
