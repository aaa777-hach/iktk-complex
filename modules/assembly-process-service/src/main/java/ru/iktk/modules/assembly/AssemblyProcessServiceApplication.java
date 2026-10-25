package ru.iktk.modules.assembly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "ru.iktk.modules.assembly",
        "ru.iktk.core"
})
@EnableFeignClients(basePackages = "ru.iktk.modules.assembly.client")
@EnableScheduling
public class AssemblyProcessServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssemblyProcessServiceApplication.class, args);
    }
}
