package br.edu.ifsp.freteja.nucleo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NucleoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NucleoApplication.class, args);
    }
}
