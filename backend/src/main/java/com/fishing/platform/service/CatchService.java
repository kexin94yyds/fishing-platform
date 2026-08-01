package com.fishing.platform.service;

import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.CatchRecord;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.dto.ApiDtos.CatchRequest;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.CatchMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatchService {
    private final CatchMapper mapper;
    private final BookingMapper bookingMapper;

    public CatchService(CatchMapper mapper, BookingMapper bookingMapper) {
        this.mapper = mapper;
        this.bookingMapper = bookingMapper;
    }

    public List<CatchRecord> findAll() {
        return mapper.findAll();
    }

    public CatchRecord create(CatchRequest request) {
        CatchAssociation association = resolveAssociation(request);
        String catchNo = BusinessNumbers.next("CR");
        mapper.insert(catchNo, request.bookingId(), association.spotId(), association.memberId(),
                association.fishingDate(),
                request.species().trim(), request.weight(), request.quantity(), request.notes(),
                request.status() == null ? "RECORDED" : request.status());
        return mapper.findByNo(catchNo);
    }

    public CatchRecord update(Long id, CatchRequest request) {
        CatchAssociation association = resolveAssociation(request);
        int changed = mapper.update(id, request.bookingId(), association.spotId(), association.memberId(),
                association.fishingDate(), request.species().trim(), request.weight(), request.quantity(),
                request.notes(), request.status() == null ? "RECORDED" : request.status());
        if (changed == 0) {
            throw new NotFoundException("渔获记录不存在");
        }
        return mapper.findById(id);
    }

    private CatchAssociation resolveAssociation(CatchRequest request) {
        if (request.bookingId() == null) {
            return new CatchAssociation(request.spotId(), request.memberId(), request.fishingDate());
        }
        Booking booking = bookingMapper.findById(request.bookingId());
        if (booking == null) {
            throw new NotFoundException("关联预订不存在");
        }
        return new CatchAssociation(booking.spotId(), booking.memberId(), booking.fishingDate());
    }

    private record CatchAssociation(Long spotId, Long memberId, java.time.LocalDate fishingDate) {
    }
}
