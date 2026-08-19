package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Spot;
import com.fishing.platform.domain.DomainModels.Zone;
import com.fishing.platform.dto.ApiDtos.SpotRequest;
import com.fishing.platform.mapper.SpotMapper;
import com.fishing.platform.mapper.ZoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class SpotService {
    private final SpotMapper mapper;
    private final ZoneMapper zoneMapper;
    private final Clock businessClock;

    public SpotService(SpotMapper mapper, ZoneMapper zoneMapper, Clock businessClock) {
        this.mapper = mapper;
        this.zoneMapper = zoneMapper;
        this.businessClock = businessClock;
    }

    public List<Spot> findAll() {
        return mapper.findAll();
    }

    public List<Spot> mapSpots() {
        return mapper.findMapSpots();
    }

    @Transactional
    public Spot create(SpotRequest request) {
        Zone zone = zoneMapper.findByIdForUpdate(request.zoneId());
        if (zone == null || !"ACTIVE".equals(zone.status())) {
            throw new BusinessException("所属分区不存在或已停用，不能创建钓位");
        }
        mapper.insert(request.zoneId(), request.code().trim(), request.name().trim(),
                request.mapX(), request.mapY(), request.capacity(),
                request.status() == null ? "OPEN" : request.status(), request.note());
        return mapper.findByCode(request.code().trim());
    }

    @Transactional
    public Spot update(Long id, SpotRequest request) {
        if (mapper.lockById(id) == null) {
            throw new NotFoundException("钓位不存在");
        }
        Spot existing = mapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("钓位不存在");
        }
        LocalDate today = LocalDate.now(businessClock);
        Zone zone = zoneMapper.findByIdForUpdate(request.zoneId());
        if (zone == null || !"ACTIVE".equals(zone.status())) {
            throw new BusinessException("所属分区不存在或已停用，不能更新钓位");
        }
        String targetStatus = request.status() == null ? existing.status() : request.status();
        if ((!"OPEN".equals(targetStatus) || !existing.zoneId().equals(request.zoneId()))
                && mapper.hasFutureConfirmedBooking(id, today)) {
            throw new BusinessException("该钓位存在未来已确认预约，不能停用或调整所属分区");
        }
        int changed = mapper.updateIfCapacityAllows(
                id, request.zoneId(), request.code().trim(), request.name().trim(),
                request.mapX(), request.mapY(), request.capacity(),
                targetStatus, request.note(), today);
        if (changed == 0) {
            int occupied = mapper.maximumActiveReservationCount(id, today);
            if (occupied > request.capacity()) {
                throw new BusinessException("钓位容量不能低于现有有效预订人数（" + occupied + " 人）");
            }
            throw new BusinessException("钓位信息已发生变化，请刷新后重试");
        }
        mapper.updateFutureInventoryCapacity(id, request.capacity(), today);
        return mapper.findById(id);
    }
}
