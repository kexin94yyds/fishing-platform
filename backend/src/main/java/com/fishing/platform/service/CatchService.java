package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.CatchRecord;
import com.fishing.platform.domain.DomainModels.Member;
import com.fishing.platform.domain.DomainModels.Spot;
import com.fishing.platform.domain.DomainModels.Zone;
import com.fishing.platform.dto.ApiDtos.CatchCreateRequest;
import com.fishing.platform.dto.ApiDtos.CatchUpdateRequest;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.CatchMapper;
import com.fishing.platform.mapper.MemberMapper;
import com.fishing.platform.mapper.SpotMapper;
import com.fishing.platform.mapper.ZoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class CatchService {
    private final CatchMapper mapper;
    private final BookingMapper bookingMapper;
    private final MemberMapper memberMapper;
    private final SpotMapper spotMapper;
    private final ZoneMapper zoneMapper;
    private final Clock businessClock;

    public CatchService(CatchMapper mapper,
                        BookingMapper bookingMapper,
                        MemberMapper memberMapper,
                        SpotMapper spotMapper,
                        ZoneMapper zoneMapper,
                        Clock businessClock) {
        this.mapper = mapper;
        this.bookingMapper = bookingMapper;
        this.memberMapper = memberMapper;
        this.spotMapper = spotMapper;
        this.zoneMapper = zoneMapper;
        this.businessClock = businessClock;
    }

    public List<CatchRecord> findAll(LocalDate date, Long zoneId, String species, Long memberId) {
        return mapper.findAll(date, zoneId, species == null ? null : species.trim(), memberId);
    }

    @Transactional
    public CatchRecord create(CatchCreateRequest request) {
        CatchAssociation association = resolveAssociation(request.bookingId(), request.spotId(),
                request.memberId(), request.fishingDate(), request.timeSlot());
        String catchNo = BusinessNumbers.next("CR");
        mapper.insert(catchNo, request.bookingId(), association.spotId(), association.memberId(),
                association.fishingDate(), association.timeSlot(),
                request.species().trim(), request.weight(), request.quantity(), request.notes(),
                "RECORDED");
        return mapper.findByNo(catchNo);
    }

    @Transactional
    public CatchRecord update(Long id, CatchUpdateRequest request) {
        if (mapper.lockById(id) == null) {
            throw new NotFoundException("渔获记录不存在");
        }
        CatchRecord existing = mapper.findById(id);
        if (!existing.status().equals(request.expectedStatus())) {
            throw new BusinessException("渔获记录已被其他操作修改，请刷新后重试");
        }
        if (!existing.version().equals(request.expectedVersion())) {
            throw new BusinessException("渔获记录已被其他操作修改，请刷新后重试");
        }
        String status = request.status();
        validateStatusTransition(existing.status(), status);
        Long bookingId = request.bookingId();
        CatchAssociation association;
        if ("VOID".equals(status)) {
            if (!Objects.equals(request.bookingId(), existing.bookingId())
                    || !Objects.equals(request.spotId(), existing.spotId())
                    || !Objects.equals(request.memberId(), existing.memberId())
                    || !Objects.equals(request.fishingDate(), existing.fishingDate())
                    || (request.timeSlot() != null && !Objects.equals(request.timeSlot(), existing.timeSlot()))) {
                throw new BusinessException("作废仅允许修改状态和渔获内容，关联预订、会员、钓位及日期必须保持不变");
            }
            bookingId = existing.bookingId();
            association = new CatchAssociation(
                    existing.spotId(), existing.memberId(), existing.fishingDate(), existing.timeSlot());
        } else {
            association = resolveAssociation(request.bookingId(), request.spotId(),
                    request.memberId(), request.fishingDate(), request.timeSlot());
        }
        int changed = mapper.update(id, bookingId, association.spotId(), association.memberId(),
                association.fishingDate(), association.timeSlot(), request.species().trim(), request.weight(), request.quantity(),
                request.notes(), status, request.expectedStatus(), request.expectedVersion());
        if (changed == 0) {
            throw new BusinessException("渔获记录已被其他操作修改，请刷新后重试");
        }
        return mapper.findById(id);
    }

    private void validateStatusTransition(String currentStatus, String targetStatus) {
        boolean allowed = switch (currentStatus) {
            case "RECORDED" -> "RECORDED".equals(targetStatus)
                    || "VERIFIED".equals(targetStatus)
                    || "VOID".equals(targetStatus);
            case "VERIFIED" -> "VERIFIED".equals(targetStatus) || "VOID".equals(targetStatus);
            case "VOID" -> "VOID".equals(targetStatus);
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException("渔获状态不允许从 " + currentStatus + " 变更为 " + targetStatus);
        }
    }

    private CatchAssociation resolveAssociation(Long bookingId,
                                                 Long spotId,
                                                 Long memberId,
                                                 LocalDate fishingDate,
                                                 String timeSlot) {
        if (bookingId == null) {
            rejectFutureCatch(fishingDate);
            validateStandaloneAssociation(memberId, spotId);
            return new CatchAssociation(spotId, memberId, fishingDate,
                    timeSlot == null || timeSlot.isBlank() ? "MORNING" : timeSlot);
        }
        if (bookingMapper.lockById(bookingId) == null) {
            throw new NotFoundException("关联预订不存在");
        }
        Booking booking = bookingMapper.findById(bookingId);
        if (!"CONFIRMED".equals(booking.status())) {
            throw new BusinessException("已取消或无效的预订不能用于渔获登记");
        }
        rejectFutureCatch(booking.fishingDate());
        return new CatchAssociation(
                booking.spotId(), booking.memberId(), booking.fishingDate(), booking.timeSlot());
    }

    private void validateStandaloneAssociation(Long memberId, Long spotId) {
        if (memberId != null) {
            Member member = memberMapper.findByIdForUpdate(memberId);
            if (member == null || !"ACTIVE".equals(member.status())) {
                throw new BusinessException("会员不存在或已停用，不能登记独立渔获");
            }
        }
        if (spotMapper.lockById(spotId) == null) {
            throw new NotFoundException("钓位不存在");
        }
        Spot spot = spotMapper.findById(spotId);
        Zone zone = zoneMapper.findByIdForUpdate(spot.zoneId());
        if (zone == null || !"ACTIVE".equals(zone.status())) {
            throw new BusinessException("钓位所属分区已停用，不能登记独立渔获");
        }
        if (!"OPEN".equals(spot.status())) {
            throw new BusinessException("钓位未开放，不能登记独立渔获");
        }
    }

    private void rejectFutureCatch(LocalDate fishingDate) {
        if (fishingDate.isAfter(LocalDate.now(businessClock))) {
            throw new BusinessException("不能提前登记未来日期的渔获");
        }
    }

    private record CatchAssociation(Long spotId, Long memberId, LocalDate fishingDate, String timeSlot) {
    }
}
