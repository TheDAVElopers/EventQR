package com.thedavelopers.eventqr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.thedavelopers.eventqr.shared.config.FreshDatabaseBootstrapCallback;

@EnableScheduling
@SpringBootApplication
// Wired here rather than via @Component so the Flyway empty-database bootstrap exists in every context,
// including sliced tests that resolve their configuration from this class.
@Import(FreshDatabaseBootstrapCallback.class)
public class EventqrApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventqrApplication.class, args);
	}

}
