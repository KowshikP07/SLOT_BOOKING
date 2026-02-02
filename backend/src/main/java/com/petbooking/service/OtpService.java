package com.petbooking.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    public void saveOtp(String key, String otp) {
        // 5 minutes expiry
        redisTemplate.opsForValue().set(key, otp, java.time.Duration.ofMinutes(5));
    }

    public String getOtp(String key) {
        Object otp = redisTemplate.opsForValue().get(key);
        return otp != null ? otp.toString() : null;
    }

    public void deleteOtp(String key) {
        redisTemplate.delete(key);
    }

    public boolean checkRateLimit(String key, int limit, long durationSeconds) {
        String rateKey = "rate_limit:" + key;

        Long currentCount = redisTemplate.opsForValue().increment(rateKey);

        if (currentCount == 1) {
            // First time key is created, set expiry
            redisTemplate.expire(rateKey, java.time.Duration.ofSeconds(durationSeconds));
        }

        return currentCount <= limit;
    }
}
