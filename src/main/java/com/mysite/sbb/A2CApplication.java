package com.mysite.sbb;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.mysite.sbb.board.BoardPostService;
import com.mysite.sbb.user.UserService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@SpringBootApplication
public class A2CApplication {

	private final BoardPostService boardPostService;
	private final UserService userService;

	public static void main(String[] args) {
		SpringApplication.run(A2CApplication.class, args);
	}

	@Bean
	ApplicationRunner boardInitializer() {
		return args -> {
			this.boardPostService.assignLegacyPostsToFreeBoard();
			this.userService.backfillDefaultRace();
			this.userService.ensureUser("admin", "admin@a2c.local", "1234");
		};
	}
}
