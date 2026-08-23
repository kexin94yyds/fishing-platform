package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.BookingAudit;
import com.fishing.platform.domain.DomainModels.Member;
import com.fishing.platform.domain.DomainModels.SlotInventory;
import com.fishing.platform.domain.DomainModels.Spot;
import com.fishing.platform.domain.DomainModels.Zone;
import com.fishing.platform.domain.DomainModels.SpotAvailability;
import com.fishing.platform.domain.DomainModels.UserAccount;
import com.fishing.platform.dto.ApiDtos.BookingRequest;
import com.fishing.platform.dto.ApiDtos.SlotInventoryRequest;
import com.fishing.platform.dto.ApiDtos.UserBookingRequest;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.MemberMapper;
import com.fishing.platform.mapper.PaymentMapper;
import com.fishing.platform.mapper.SpotMapper;
import com.fishing.platform.mapper.ZoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class BookingService {
    private final BookingMapper mapper;
    private final SpotMapper spotMapper;
    private final MemberMapper memberMapper;
    private final PaymentMapper paymentMapper;
    private final ZoneMapper zoneMapper;
    private final CurrentUserService currentUserService;
    private final Clock businessClock;

    public BookingService(BookingMapper mapper, SpotMapper spotMapper, MemberMapper memberMapper,
                          PaymentMapper paymentMapper,
                          ZoneMapper zoneMapper,
                          CurrentUserService currentUserService, Clock businessClock) {
        this.mapper = mapper;
        this.spotMapper = spotMapper;
        this.memberMapper = memberMapper;
        this.paymentMapper = paymentMapper;
        this.zoneMapper = zoneMapper;
        this.currentUserService = currentUserService;
        this.businessClock = businessClock;
    }

    public List<Booking> findAll(String status, LocalDate date, Long memberId) {
        return mapper.findAll(status, date, memberId);
    }

    public List<Booking> findCurrentUserBookings() {
        UserAccount actor = requireCurrentUser();
        return mapper.findAllByUserId(actor.id());
    }

    public List<SpotAvailability> availability(LocalDate date, String timeSlot, Long spotId) {
        LocalDate selectedDate = date == null ? LocalDate.now(businessClock) : date;
        String selectedSlot = timeSlot == null || timeSlot.isBlank() ? "MORNING" : timeSlot.trim();
        return mapper.availability(selectedDate, selectedSlot, spotId);
    }

    public List<SpotAvailability> availabilityForCurrentUser(LocalDate date, String timeSlot, Long spotId) {
        requireCurrentUser();
        LocalDate selectedDate = date == null ? LocalDate.now(businessClock) : date;
        if (selectedDate.isBefore(LocalDate.now(businessClock))) {
            throw new BusinessException("垂钓日期不能早于今天");
        }
        return availability(selectedDate, timeSlot, spotId);
    }

    public List<SlotInventory> findSlotInventories(LocalDate date, Long spotId) {
        LocalDate selectedDate = date == null ? LocalDate.now(businessClock) : date;
        return mapper.findInventories(selectedDate, spotId);
    }

    public List<BookingAudit> audits(Long id, int requestedLimit) {
        if (mapper.findById(id) == null) {
            throw new NotFoundException("预订不存在");
        }
        return mapper.findAudits(id, Math.max(1, Math.min(requestedLimit, 100)));
    }

    @Transactional
    public SlotInventory configureSlot(Long spotId, SlotInventoryRequest request) {
        LocalDate today = LocalDate.now(businessClock);
        if (request.fishingDate().isBefore(today)) {
            throw new BusinessException("不能修改过去日期的时段配置");
        }
        if (spotMapper.lockById(spotId) == null) {
            throw new NotFoundException("钓位不存在");
        }
        Spot spot = spotMapper.findById(spotId);
        Zone zone = zoneMapper.findByIdForUpdate(spot.zoneId());
        if (zone == null || !"ACTIVE".equals(zone.status())) {
            throw new BusinessException("钓位所属分区已停用，不能配置时段");
        }
        if (request.capacity() > spot.capacity()) {
            throw new BusinessException("时段容量不能超过钓位基础容量（" + spot.capacity() + " 人）");
        }

        SlotInventory existing = mapper.findInventoryForUpdate(
                spotId, request.fishingDate(), request.timeSlot());
        if (existing == null) {
            mapper.insertInventory(spotId, request.fishingDate(), request.timeSlot(),
                    request.capacity(), request.price(), request.status());
        } else {
            if (request.expectedVersion() == null || !existing.version().equals(request.expectedVersion())) {
                throw new BusinessException("时段配置已被其他人修改，请刷新后重试");
            }
            if (existing.reservedCount() > request.capacity()) {
                throw new BusinessException("时段容量不能低于已预订人数（" + existing.reservedCount() + " 人）");
            }
            if (mapper.updateInventory(existing.id(), request.capacity(), request.price(),
                    request.status(), request.expectedVersion()) == 0) {
                throw new BusinessException("时段配置已发生变化，请刷新后重试");
            }
        }
        return mapper.findInventoryForUpdate(spotId, request.fishingDate(), request.timeSlot());
    }

    @Transactional
    public Booking create(BookingRequest request) {
        rejectPastBookingDate(request.fishingDate());
        UserAccount actor = currentUserService.current();
        return createForActor(request, actor, null);
    }

    @Transactional
    public Booking createForCurrentUser(UserBookingRequest request) {
        UserAccount actor = requireCurrentUser();
        BookingRequest bookingRequest = new BookingRequest(
                null,
                actor.displayName(),
                request.contactPhone().trim(),
                request.spotId(),
                request.fishingDate(),
                request.timeSlot(),
                request.guests(),
                null,
                request.notes());
        return createForActor(bookingRequest, actor, "U:" + actor.id());
    }

    private Booking createForActor(BookingRequest request, UserAccount actor, String forcedCustomerKey) {
        rejectPastBookingDate(request.fishingDate());
        Member member = null;
        if (request.memberId() != null) {
            member = memberMapper.findByIdForUpdate(request.memberId());
            if (member == null || !"ACTIVE".equals(member.status())) {
                throw new BusinessException("会员不存在或已停用，不能创建预订");
            }
        }
        String contactName = member == null ? request.contactName().trim() : member.name();
        String contactPhone = member == null ? request.contactPhone().trim() : member.phone();
        String customerKey = forcedCustomerKey != null
                ? forcedCustomerKey
                : member == null
                    ? "P:" + normalizePhone(contactPhone)
                    : "M:" + member.id();
        String contactKey = "P:" + normalizePhone(contactPhone);
        if (mapper.hasActiveCustomerBooking(request.fishingDate(), request.timeSlot(), customerKey)) {
            throw new BusinessException("该顾客在所选日期和时段已有有效预订");
        }
        if (mapper.hasActiveContactBooking(request.fishingDate(), request.timeSlot(), contactKey)) {
            throw new BusinessException("该联系电话在所选日期和时段已有有效预订");
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
        SlotInventory inventory = mapper.findInventoryForUpdate(
                request.spotId(), request.fishingDate(), request.timeSlot());
        int reserved = mapper.reserve(request.spotId(), request.fishingDate(), request.timeSlot(), request.guests());
        if (reserved == 0) {
            throw new BusinessException("该钓位在所选日期和时段余量不足或不可预订");
        }

        String bookingNo = BusinessNumbers.next("BK");
        BigDecimal amount = request.amount() == null
                ? inventory.price().multiply(BigDecimal.valueOf(request.guests()))
                : request.amount();
        String paymentStatus = amount.signum() == 0 ? "PAID" : "PENDING";
        mapper.insertBooking(bookingNo, request.memberId(), contactName, contactPhone, customerKey, contactKey,
                request.spotId(), actor.id(),
                request.fishingDate(), request.timeSlot(), request.guests(),
                amount, paymentStatus, request.notes());
        Booking created = mapper.findByNo(bookingNo);
        if (amount.signum() > 0) {
            paymentMapper.insert(BusinessNumbers.next("PAY"), "BOOKING", created.id(), amount, null);
        }
        mapper.insertAudit(actor.id(), actor.username(), created.id(), created.bookingNo(), "CREATE",
                null, created.status(), null, created.paymentStatus());
        return created;
    }

    @Transactional
    public Booking cancel(Long id) {
        UserAccount actor = currentUserService.current();
        Booking existing = lockConfirmedBooking(id);
        return cancelLocked(existing, actor);
    }

    @Transactional
    public Booking cancelCurrentUserBooking(Long id) {
        UserAccount actor = requireCurrentUser();
        if (mapper.lockByIdForUser(id, actor.id()) == null) {
            throw new NotFoundException("预订不存在");
        }
        Booking existing = mapper.findById(id);
        if (!"CONFIRMED".equals(existing.status())) {
            throw new BusinessException("预订当前状态不允许该操作");
        }
        return cancelLocked(existing, actor);
    }

    private Booking cancelLocked(Booking existing, UserAccount actor) {
        LocalDate today = LocalDate.now(businessClock);
        if (!existing.fishingDate().isAfter(today)) {
            throw new BusinessException("垂钓日当天或之前不能取消，请由管理员结单");
        }
        if (mapper.hasActiveCatch(existing.id())) {
            throw new BusinessException("该预订已有有效渔获记录，不能取消");
        }
        if (mapper.cancel(existing.id()) == 0) {
            throw new BusinessException("预订已取消或当前状态不允许取消");
        }
        if (mapper.release(existing.spotId(), existing.fishingDate(), existing.timeSlot(), existing.guests()) == 0) {
            throw new BusinessException("预订库存释放失败");
        }
        paymentMapper.cancelByBusiness("BOOKING", existing.id());
        Booking cancelled = mapper.findById(existing.id());
        audit(actor, existing, cancelled, "CANCEL");
        return cancelled;
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
        Booking settled = mapper.findById(id);
        audit(existing, settled, targetStatus);
        return settled;
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

    private void audit(Booking before, Booking after, String action) {
        var actor = currentUserService.current();
        audit(actor, before, after, action);
    }

    private void audit(UserAccount actor, Booking before, Booking after, String action) {
        mapper.insertAudit(actor.id(), actor.username(), after.id(), after.bookingNo(), action,
                before.status(), after.status(), before.paymentStatus(), after.paymentStatus());
    }

    private UserAccount requireCurrentUser() {
        UserAccount actor = currentUserService.current();
        if (!"USER".equals(actor.role())) {
            throw new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "仅钓友用户可访问");
        }
        return actor;
    }

    private String normalizePhone(String phone) {
        return phone.replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private void rejectPastBookingDate(LocalDate fishingDate) {
        if (fishingDate.isBefore(LocalDate.now(businessClock))) {
            throw new BusinessException("垂钓日期不能早于今天");
        }
    }
}
