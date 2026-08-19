package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.Spot;
import com.fishing.platform.domain.DomainModels.Zone;
import com.fishing.platform.domain.DomainModels.SpotAvailability;
import com.fishing.platform.dto.ApiDtos.BookingRequest;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.MemberMapper;
import com.fishing.platform.mapper.SpotMapper;
import com.fishing.platform.mapper.ZoneMapper;
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
    private final ZoneMapper zoneMapper;
    private final CurrentUserService currentUserService;
    private final Clock businessClock;

    public BookingService(BookingMapper mapper, SpotMapper spotMapper, MemberMapper memberMapper,
                          ZoneMapper zoneMapper,
                          CurrentUserService currentUserService, Clock businessClock) {
        this.mapper = mapper;
        this.spotMapper = spotMapper;
        this.memberMapper = memberMapper;
        this.zoneMapper = zoneMapper;
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
        Spot spot = spotMapper.findById(request.spotId());
        if (spot == null) {
            throw new NotFoundException("钓位不存在");
        }
        Zone zone = zoneMapper.findByIdForUpdate(spot.zoneId());
        if (zone == null || !"ACTIVE".equals(zone.status())) {
            throw new BusinessException("钓位所属分区已停用，不能创建预订");
        }
        if (!"OPEN".equals(spot.status())) {
            throw new BusinessException("钓位未开放，不能创建预订");
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
        Booking existing = lockConfirmedBooking(id);
        LocalDate today = LocalDate.now(businessClock);
        if (!existing.fishingDate().isAfter(today)) {
            throw new BusinessException("垂钓日当天或之前不能取消，请由管理员结单");
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

    @Transactional
    public Booking complete(Long id) {
        return settle(id, "COMPLETED", false);
    }

    @Transactional
    public Booking noShow(Long id) {
        return settle(id, "NO_SHOW", true);
    }

    private Booking settle(Long id, String targetStatus, boolean rejectActiveCatch) {
        Booking existing = lockConfirmedBooking(id);
        if (existing.fishingDate().isAfter(LocalDate.now(businessClock))) {
            throw new BusinessException("未来预约不能结单");
        }
        if (rejectActiveCatch && mapper.hasActiveCatch(id)) {
            throw new BusinessException("该预订已有有效渔获记录，只能完成结单，不能标记爽约");
        }
        if (mapper.settle(id, targetStatus) == 0) {
            throw new BusinessException("预订状态已发生变化，请刷新后重试");
        }
        return mapper.findById(id);
    }

    private Booking lockConfirmedBooking(Long id) {
        if (mapper.lockById(id) == null) {
            throw new NotFoundException("预订不存在");
        }
        Booking existing = mapper.findById(id);
        if (!"CONFIRMED".equals(existing.status())) {
            throw new BusinessException("预订当前状态不允许该操作");
        }
        return existing;
    }
}
