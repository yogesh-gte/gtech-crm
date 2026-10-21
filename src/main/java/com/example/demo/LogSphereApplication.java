package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.File;

@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
public class LogSphereApplication extends SpringBootServletInitializer {

	static {
		configureGlobalStorageSettings();
	}

	public static void main(String[] args) {
		configureGlobalStorageSettings();
		SpringApplication.run(LogSphereApplication.class, args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		configureGlobalStorageSettings();
		return application.sources(LogSphereApplication.class);
	}

	private static void configureGlobalStorageSettings() {
		// 1. Disable ImageIO disk caching globally to operate 100% in memory
		try {
			javax.imageio.ImageIO.setUseCache(false);
		} catch (Throwable ignored) {}

		// 2. Redirect JVM temp directory to Drive D: if available to protect constrained C: drive
		try {
			File dTemp = new File("D:\\temp");
			if (dTemp.exists() || dTemp.mkdirs()) {
				System.setProperty("java.io.tmpdir", "D:\\temp");
			}
		} catch (Throwable ignored) {}
	}
}
