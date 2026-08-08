package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.SpotAvailability;
import com.fishing.platform.dto.ApiDtos.BookingRequest;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.MemberMapper;
import com.fishing.platform.mapper.SpotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {
    private final BookingMapper mapper;
    private final SpotMapper spotMapper;
    private final MemberMapper memberMapper;
    private final CurrentUserService currentUserService;
    private final Clock businessClock;

    public BookingService(BookingMapper mapper, SpotMapper spotMapper, MemberMapper memberMapper,
                          CurrentUserService currentUserService, Clock businessClock) {
        this.mapper = mapper;
        this.spotMapper = spotMapper;
        this.memberMapper = memberMapper;
        this.currentUserService = currentUserService;
        this.businessClock = businessClock;
    }

    public List<Booking> findAll(String status, LocalDate date, Long memberId) {
        return mapper.findAll(status, date, memberId);
    }

    public List<SpotAvailability> availability(LocalDate date, String timeSlot, Long spotId) {
        LocalDate selectedDate = date == null ? LocalDate.now(businessClock) : date;
        String selectedSlot = timeSlot == null || timeSlot.isBlank() ? "MORNING" : timeSlot.trim();
        return mapper.availability(selectedDate, selectedSlot, spotId);
    }

    @Transactional
    public Booking create(BookingRequest request) {
        if (request.fishingDate().isBefore(LocalDate.now(businessClock))) {
            throw new BusinessException("垂钓日期不能早于今天");
        }
        if (request.memberId() != null) {
            var member = memberMapper.findByIdForUpdate(request.memberId());
            if (member == null || !"ACTIVE".equals(member.status())) {
                throw new BusinessException("会员不存在或已停用，不能创建预订");
            }
        }
        if (spotMapper.lockById(request.spotId()) == null) {
            throw new NotFoundException("钓位不存在");
        }
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
        Booking existing = mapper.findByIdForUpdate(id);
        if (existing == null) {
            throw new NotFoundException("预订不存在");
        }
        if (!"CONFIRMED".equals(existing.status())) {
            throw new BusinessException("预订已取消或当前状态不允许取消");
        }
        if (mapper.hasActiveCatch(id)) {
            throw new BusinessException("该预订已有有效渔获记录，不能取消");
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
