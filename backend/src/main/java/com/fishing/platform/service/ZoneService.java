package com.fishing.platform.service;

import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Zone;
import com.fishing.platform.dto.ApiDtos.ZoneRequest;
import com.fishing.platform.mapper.ZoneMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZoneService {
    private final ZoneMapper mapper;

    public ZoneService(ZoneMapper mapper) {
        this.mapper = mapper;
    }

    public List<Zone> findAll() {
        return mapper.findAll();
    }

    public Zone create(ZoneRequest request) {
        mapper.insert(request.code().trim(), request.name().trim(), request.description(),
                request.status() == null ? "ACTIVE" : request.status());
        return mapper.findByCode(request.code().trim());
    }

    public Zone update(Long id, ZoneRequest request) {
        int changed = mapper.update(id, request.code().trim(), request.name().trim(), request.description(),
                request.status() == null ? "ACTIVE" : request.status());
        if (changed == 0) {
            throw new NotFoundException("运营分区不存在");
        }
        return mapper.findById(id);
    }
}
