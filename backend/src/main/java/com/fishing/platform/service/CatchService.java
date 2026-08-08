package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.CatchRecord;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.dto.ApiDtos.CatchRequest;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.CatchMapper;
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
    private final Clock businessClock;

    public CatchService(CatchMapper mapper, BookingMapper bookingMapper, Clock businessClock) {
        this.mapper = mapper;
        this.bookingMapper = bookingMapper;
        this.businessClock = businessClock;
    }

    public List<CatchRecord> findAll() {
        return mapper.findAll();
    }

    @Transactional
    public CatchRecord create(CatchRequest request) {
        CatchAssociation association = resolveAssociation(request);
        String catchNo = BusinessNumbers.next("CR");
        mapper.insert(catchNo, request.bookingId(), association.spotId(), association.memberId(),
                association.fishingDate(),
                request.species().trim(), request.weight(), request.quantity(), request.notes(),
                request.status() == null ? "RECORDED" : request.status());
        return mapper.findByNo(catchNo);
    }

    @Transactional
    public CatchRecord update(Long id, CatchRequest request) {
        String status = request.status() == null ? "RECORDED" : request.status();
        Long bookingId = request.bookingId();
        CatchAssociation association;
        if ("VOID".equals(status)) {
            CatchRecord existing = mapper.findById(id);
            if (existing == null) {
                throw new NotFoundException("渔获记录不存在");
            }
            if (!Objects.equals(request.bookingId(), existing.bookingId())
                    || !Objects.equals(request.spotId(), existing.spotId())
                    || !Objects.equals(request.memberId(), existing.memberId())
                    || !Objects.equals(request.fishingDate(), existing.fishingDate())) {
                throw new BusinessException("作废仅允许修改状态和渔获内容，关联预订、会员、钓位及日期必须保持不变");
            }
            bookingId = existing.bookingId();
            association = new CatchAssociation(existing.spotId(), existing.memberId(), existing.fishingDate());
        } else {
            association = resolveAssociation(request);
        }
        int changed = mapper.update(id, bookingId, association.spotId(), association.memberId(),
                association.fishingDate(), request.species().trim(), request.weight(), request.quantity(),
                request.notes(), status);
        if (changed == 0) {
            throw new NotFoundException("渔获记录不存在");
        }
        return mapper.findById(id);
    }

    private CatchAssociation resolveAssociation(CatchRequest request) {
        if (request.bookingId() == null) {
            rejectFutureCatch(request.fishingDate());
            return new CatchAssociation(request.spotId(), request.memberId(), request.fishingDate());
        }
        Booking booking = bookingMapper.findByIdForUpdate(request.bookingId());
        if (booking == null) {
            throw new NotFoundException("关联预订不存在");
        }
        if (!"CONFIRMED".equals(booking.status())) {
            throw new BusinessException("已取消或无效的预订不能用于渔获登记");
        }
        rejectFutureCatch(booking.fishingDate());
        return new CatchAssociation(booking.spotId(), booking.memberId(), booking.fishingDate());
    }

    private void rejectFutureCatch(LocalDate fishingDate) {
        if (fishingDate.isAfter(LocalDate.now(businessClock))) {
            throw new BusinessException("不能提前登记未来日期的渔获");
        }
    }

    private record CatchAssociation(Long spotId, Long memberId, LocalDate fishingDate) {
    }
}
