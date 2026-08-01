package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.SpotAvailability;
import com.fishing.platform.dto.ApiDtos.BookingRequest;
import com.fishing.platform.mapper.BookingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {
    private final BookingMapper mapper;
    private final CurrentUserService currentUserService;

    public BookingService(BookingMapper mapper, CurrentUserService currentUserService) {
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public List<Booking> findAll(String status, LocalDate date, Long memberId) {
        return mapper.findAll(status, date, memberId);
    }

    public List<SpotAvailability> availability(LocalDate date, String timeSlot, Long spotId) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        String selectedSlot = timeSlot == null || timeSlot.isBlank() ? "MORNING" : timeSlot.trim();
        return mapper.availability(selectedDate, selectedSlot, spotId);
    }

    @Transactional
    public Booking create(BookingRequest request) {
        mapper.insertInventoryIfMissing(request.spotId(), request.fishingDate(), request.timeSlot());
        int reserved = mapper.reserve(request.spotId(), request.fishingDate(), request.timeSlot(), request.guests());
        if (reserved == 0) {
            throw new BusinessException("该钓位在所选日期和时段余量不足或不可预订");
        }

        String bookingNo = BusinessNumbers.next("BK");
        mapper.insertBooking(bookingNo, request.memberId(), request.spotId(), currentUserService.current().id(),
                request.fishingDate(), request.timeSlot(), request.guests(),
                request.amount() == null ? BigDecimal.ZERO : request.amount(), request.notes());
        return mapper.findByNo(bookingNo);
    }

    @Transactional
    public Booking cancel(Long id) {
        Booking existing = mapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("预订不存在");
        }
        if (mapper.cancel(id) == 0) {
            throw new BusinessException("预订已取消或当前状态不允许取消");
        }
        if (mapper.release(existing.spotId(), existing.fishingDate(), existing.timeSlot(), existing.guests()) == 0) {
            throw new BusinessException("预订库存释放失败");
        }
        return mapper.findById(id);
    }
}
