package de.challenge.appointments;

import org.springframework.boot.SpringApplication;

public class TestAppointmentsApplication {

	public static void main(String[] args) {
		SpringApplication.from(AppointmentsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
