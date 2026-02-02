package com.petbooking.controller;

import com.petbooking.dto.Dtos;
import com.petbooking.entity.Booking;
import com.petbooking.entity.Slot;
import com.petbooking.entity.Student;
import com.petbooking.repository.SlotRepository;
import com.petbooking.repository.StudentRepository;
import com.petbooking.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private BookingService bookingService;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/slots")
    public ResponseEntity<List<Slot>> getAvailableSlots(Authentication auth) {
        String rollNo = auth.getName();
        Student student = studentRepository.findById(rollNo).orElseThrow();

        String key = "slots:available:" + student.getCategory();

        // 1. Check Cache
        List<Slot> cachedSlots = (List<Slot>) redisTemplate.opsForValue().get(key);
        if (cachedSlots != null) {
            System.out.println("Cache HIT for available slots (Category: " + student.getCategory() + ")");
            return ResponseEntity.ok(cachedSlots);
        }

        // 2. Cache Miss - Fetch from DB
        System.out.println("Cache MISS for available slots (Category: " + student.getCategory() + ")");
        List<Slot> slots = slotRepository.findByCategoryAndBookingOpenTrue(student.getCategory());

        // 3. Store in Cache (e.g., for 5 minutes)
        if (slots != null && !slots.isEmpty()) {
            redisTemplate.opsForValue().set(key, slots, java.time.Duration.ofSeconds(10));
        }

        return ResponseEntity.ok(slots);
    }

    @PostMapping("/book")
    public ResponseEntity<?> bookSlot(@RequestBody Dtos.BookingRequest request, Authentication auth) {
        String rollNo = auth.getName();
        try {
            Booking booking = bookingService.bookSlot(rollNo, request.getSlotId());
            return ResponseEntity.ok(booking);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
