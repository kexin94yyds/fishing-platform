package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Zone;
import com.fishing.platform.dto.ApiDtos.ZoneRequest;
import com.fishing.platform.mapper.ZoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class ZoneService {
    private final ZoneMapper mapper;
    private final Clock businessClock;

    public ZoneService(ZoneMapper mapper, Clock businessClock) {
        this.mapper = mapper;
        this.businessClock = businessClock;
    }

    public List<Zone> findAll() {
        return mapper.findAll();
    }

    public Zone create(ZoneRequest request) {
        mapper.insert(request.code().trim(), request.name().trim(), request.description(),
                request.status() == null ? "ACTIVE" : request.status());
        return mapper.findByCode(request.code().trim());
    }

    @Transactional
    public Zone update(Long id, ZoneRequest request) {
        Zone existing = mapper.findByIdForUpdate(id);
        if (existing == null) {
            throw new NotFoundException("运营分区不存在");
        }
        String targetStatus = request.status() == null ? existing.status() : request.status();
        if ("INACTIVE".equals(targetStatus)
                && mapper.hasFutureConfirmedBooking(id, LocalDate.now(businessClock))) {
            throw new BusinessException("该分区存在未来已确认预约，不能停用");
        }
        int changed = mapper.update(id, request.code().trim(), request.name().trim(), request.description(),
                targetStatus);
        if (changed == 0) {
            throw new NotFoundException("运营分区不存在");
        }
        return mapper.findById(id);
    }
}
