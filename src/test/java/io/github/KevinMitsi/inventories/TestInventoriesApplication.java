package io.github.KevinMitsi.inventories;

import org.springframework.boot.SpringApplication;

public class TestInventoriesApplication {

	public static void main(String[] args) {
		SpringApplication.from(InventoriesApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
