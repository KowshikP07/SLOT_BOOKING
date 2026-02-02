package com.petbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PetBookingApplication {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PetBookingApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(PetBookingApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner checkRedisConnection(
			org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {
		return args -> {
			try {
				redisTemplate.opsForValue().set("test-connection", "Redis is working!");
				Object value = redisTemplate.opsForValue().get("test-connection");
				log.info("Redis Connection Successful: " + value);
			} catch (Exception e) {
				log.error("Redis Connection Failed: " + e.getMessage());
			}
		};
	}

}
