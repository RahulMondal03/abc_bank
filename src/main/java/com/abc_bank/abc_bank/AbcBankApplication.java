package com.abc_bank.abc_bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AbcBankApplication {

	public static void main(String[] args) {
		SpringApplication.run(AbcBankApplication.class, args);
	}
}
