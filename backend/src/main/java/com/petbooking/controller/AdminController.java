package com.petbooking.controller;

import com.petbooking.dto.Dtos;
import com.petbooking.entity.Booking;
import com.petbooking.entity.DeptExamStrength;
import com.petbooking.repository.BookingRepository;
import com.petbooking.repository.DeptExamStrengthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private DeptExamStrengthRepository deptExamStrengthRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private com.petbooking.repository.DepartmentRepository departmentRepository;

    @GetMapping("/departments")
    public ResponseEntity<?> getAllDepartments() {
        return ResponseEntity.ok(departmentRepository.findAll());
    }

    @GetMapping("/strength")
    public ResponseEntity<?> getAllStrengths() {
        String key = "dept:strength:all";
        // 1. Check Cache
        Object cachedStrengths = redisTemplate.opsForValue().get(key);
        if (cachedStrengths != null) {
            System.out.println("Cache HIT for Dept Strength");
            return ResponseEntity.ok(cachedStrengths);
        }

        // 2. Cache Miss
        System.out.println("Cache MISS for Dept Strength");
        List<DeptExamStrength> strengths = deptExamStrengthRepository.findAll();

        // 3. Store in Cache
        redisTemplate.opsForValue().set(key, strengths, java.time.Duration.ofSeconds(10));

        return ResponseEntity.ok(strengths);
    }

    @PostMapping("/strength")
    public ResponseEntity<?> updateDeptStrength(@RequestBody Dtos.DeptStrengthRequest request) {
        // Create or update strength for department
        DeptExamStrength strength = deptExamStrengthRepository.findByDepartmentDeptId(request.getDeptId())
                .orElse(new DeptExamStrength());

        com.petbooking.entity.Department dept = new com.petbooking.entity.Department();
        dept.setDeptId(request.getDeptId());
        strength.setDepartment(dept);
        strength.setDayCount(request.getDayCount());
        strength.setHostelMaleCount(request.getHostelMaleCount());
        strength.setHostelFemaleCount(request.getHostelFemaleCount());

        DeptExamStrength saved = deptExamStrengthRepository.save(strength);

        // Clear Cache
        redisTemplate.delete("dept:strength:all");
        System.out.println("Cleared dept strength cache");

        return ResponseEntity.ok(saved);
    }

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/bookings")
    public ResponseEntity<List<Booking>> getAllBookings(
            @RequestParam(required = false) Long slotId,
            @RequestParam(required = false) Long deptId) {

        String key = "bookings:all";
        if (slotId != null)
            key = "bookings:slot:" + slotId;
        else if (deptId != null)
            key = "bookings:dept:" + deptId;

        // 1. Check Cache
        List<Booking> cachedBookings = (List<Booking>) redisTemplate.opsForValue().get(key);
        if (cachedBookings != null) {
            System.out.println("Cache HIT for Bookings (Key: " + key + ")");
            return ResponseEntity.ok(cachedBookings);
        }

        System.out.println("Cache MISS for Bookings (Key: " + key + ")");
        List<Booking> bookings;

        if (slotId != null) {
            bookings = bookingRepository.findBySlotSlotId(slotId);
        } else if (deptId != null) {
            bookings = bookingRepository.findByDepartmentDeptId(deptId);
        } else {
            bookings = bookingRepository.findAll();
        }

        // 2. Store in Cache (e.g. 5 mins)
        if (!bookings.isEmpty()) {
            redisTemplate.opsForValue().set(key, bookings, java.time.Duration.ofSeconds(10));
        }

        return ResponseEntity.ok(bookings);
    }
}
