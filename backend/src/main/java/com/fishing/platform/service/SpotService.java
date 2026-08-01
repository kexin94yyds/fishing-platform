package com.fishing.platform.service;

import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Spot;
import com.fishing.platform.dto.ApiDtos.SpotRequest;
import com.fishing.platform.mapper.SpotMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpotService {
    private final SpotMapper mapper;

    public SpotService(SpotMapper mapper) {
        this.mapper = mapper;
    }

    public List<Spot> findAll() {
        return mapper.findAll();
    }

    public List<Spot> mapSpots() {
        return mapper.findMapSpots();
    }

    public Spot create(SpotRequest request) {
        mapper.insert(request.zoneId(), request.code().trim(), request.name().trim(),
                request.mapX(), request.mapY(), request.capacity(),
                request.status() == null ? "OPEN" : request.status(), request.note());
        return mapper.findByCode(request.code().trim());
    }

    public Spot update(Long id, SpotRequest request) {
        int changed = mapper.update(id, request.zoneId(), request.code().trim(), request.name().trim(),
                request.mapX(), request.mapY(), request.capacity(),
                request.status() == null ? "OPEN" : request.status(), request.note());
        if (changed == 0) {
            throw new NotFoundException("钓位不存在");
        }
        return mapper.findById(id);
    }
}
