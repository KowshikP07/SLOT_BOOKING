package com.petbooking.controller;

import com.petbooking.dto.Dtos;
import com.petbooking.entity.DeptQuota;
import com.petbooking.entity.Slot;
import com.petbooking.repository.DeptQuotaRepository;
import com.petbooking.repository.SlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/slots")
@PreAuthorize("hasRole('ADMIN')")
public class SlotController {

    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private DeptQuotaRepository deptQuotaRepository;

    @Autowired
    private com.petbooking.repository.DepartmentRepository departmentRepository;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/{slotId}")
    public ResponseEntity<?> getSlotById(@PathVariable Long slotId) {
        String key = "slot:" + slotId;

        // 1. Check Cache
        Object cachedSlot = redisTemplate.opsForValue().get(key);
        if (cachedSlot != null) {
            System.out.println("Cache HIT for slotId: " + slotId);
            return ResponseEntity.ok(cachedSlot);
        }

        // 2. Cache Miss - Fetch from DB
        System.out.println("Cache MISS for slotId: " + slotId);
        return slotRepository.findById(slotId).map(slot -> {
            // 3. Store in Cache (e.g., for 10 minutes)
            redisTemplate.opsForValue().set(key, slot, java.time.Duration.ofSeconds(10));
            return ResponseEntity.ok(slot);
        }).orElse(ResponseEntity.notFound().build());
    }

    private void clearSlotCache() {
        java.util.Set<String> keys = redisTemplate.keys("slots:available:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            System.out.println("Cleared student slot cache keys: " + keys);
        }
        redisTemplate.delete("slots:admin:all");
        System.out.println("Cleared admin all slots cache");
    }

    @GetMapping
    public ResponseEntity<List<Slot>> getAllSlots() {
        String key = "slots:admin:all";
        // 1. Check Cache
        Object cachedSlots = redisTemplate.opsForValue().get(key);
        if (cachedSlots != null) {
            System.out.println("Cache HIT for ALL slots (Admin)");
            return ResponseEntity.ok((List<Slot>) cachedSlots);
        }

        System.out.println("Cache MISS for ALL slots (Admin)");
        List<Slot> slots = slotRepository.findAll();

        // 2. Store
        redisTemplate.opsForValue().set(key, slots, java.time.Duration.ofSeconds(10));

        return ResponseEntity.ok(slots);
    }

    @PostMapping
    public ResponseEntity<?> createSlot(@RequestBody Dtos.CreateSlotRequest request) {
        // 1. Map DTO to Slot
        Slot slot = new Slot();
        slot.setExamDate(LocalDate.parse(request.getExamDate()));
        slot.setStartTime(LocalTime.parse(request.getStartTime()));
        slot.setEndTime(LocalTime.parse(request.getEndTime()));
        slot.setCategory(com.petbooking.entity.Student.StudentCategory.valueOf(request.getCategory()));
        slot.setPurpose(request.getPurpose());
        slot.setBookingOpen(true); // Default open

        if (slot.getStartTime().isAfter(slot.getEndTime())) {
            return ResponseEntity.badRequest().body("Invalid time range");
        }

        Slot savedSlot = slotRepository.save(slot);

        // 2. Iterate Quotas
        if (request.getQuotas() != null) {
            for (Dtos.DeptQuotaRequest q : request.getQuotas()) {
                DeptQuota dq = new DeptQuota();
                dq.setSlot(savedSlot);
                com.petbooking.entity.Department dept = new com.petbooking.entity.Department();
                dept.setDeptId(q.getDeptId());
                dq.setDepartment(dept);
                dq.setQuotaCapacity(q.getQuota());
                dq.setBookedCount(0);
                deptQuotaRepository.save(dq);
            }
        }

        clearSlotCache(); // Evict Cache
        return ResponseEntity.ok(savedSlot);
    }

    @PutMapping("/{slotId}")
    public ResponseEntity<?> updateSlot(@PathVariable Long slotId, @RequestBody Dtos.CreateSlotRequest request) {
        return slotRepository.findById(slotId).map(slot -> {
            slot.setExamDate(LocalDate.parse(request.getExamDate()));
            slot.setStartTime(LocalTime.parse(request.getStartTime()));
            slot.setEndTime(LocalTime.parse(request.getEndTime()));
            slot.setCategory(com.petbooking.entity.Student.StudentCategory.valueOf(request.getCategory()));
            slot.setPurpose(request.getPurpose());

            Slot saved = slotRepository.save(slot);
            clearSlotCache(); // Evict Cache
            redisTemplate.delete("slot:" + slotId); // Clear single slot

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<?> deleteSlot(@PathVariable Long slotId) {
        if (!slotRepository.existsById(slotId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            slotRepository.deleteById(slotId);
            clearSlotCache(); // Evict Cache
            redisTemplate.delete("slot:" + slotId); // Clear single slot
            return ResponseEntity.ok("Slot deleted");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Cannot delete slot (likely has bookings/quotas)");
        }
    }

    @PatchMapping("/{slotId}/toggle")
    public ResponseEntity<?> toggleSlotStatus(@PathVariable Long slotId) {
        return slotRepository.findById(slotId).map(slot -> {
            slot.setBookingOpen(!slot.isBookingOpen());
            slotRepository.save(slot);

            clearSlotCache(); // Evict Cache
            redisTemplate.delete("slot:" + slotId); // Clear single slot

            return ResponseEntity.ok(slot);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{slotId}/quotas")
    public ResponseEntity<?> setQuota(@PathVariable Long slotId, @RequestBody DeptQuota quota) {
        // Validate slot exists, dept exists...
        // Simplified
        Slot s = new Slot();
        s.setSlotId(slotId);
        quota.setSlot(s);
        deptQuotaRepository.save(quota);
        return ResponseEntity.ok("Quota set");
    }
}
