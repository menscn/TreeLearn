package com.treelearn.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Treelearn local web service entry point.
 *
 * <p>The service is loopback-only ({@code server.address=127.0.0.1} in
 * application.properties): the browser client is served same-origin from this
 * process and the API is never exposed beyond the local machine.
 */
@SpringBootApplication
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
