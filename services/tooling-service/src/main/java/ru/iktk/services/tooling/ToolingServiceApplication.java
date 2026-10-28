package ru.iktk.services.tooling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа справочника режущего инструмента ИКТК.
 */
@SpringBootApplication
public class ToolingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToolingServiceApplication.class, args);
    }
}