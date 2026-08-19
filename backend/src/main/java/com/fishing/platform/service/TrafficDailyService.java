package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.domain.DomainModels.TrafficDailyEntry;
import com.fishing.platform.dto.ApiDtos.TrafficDailyUpsertRequest;
import com.fishing.platform.mapper.TrafficDailyMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class TrafficDailyService {
    private static final String STALE_MESSAGE = "客流日汇总已被其他操作修改，请刷新后重试";

    private final TrafficDailyMapper mapper;
    private final CurrentUserService currentUserService;
    private final Clock businessClock;

    public TrafficDailyService(TrafficDailyMapper mapper,
                               CurrentUserService currentUserService,
                               Clock businessClock) {
        this.mapper = mapper;
        this.currentUserService = currentUserService;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true)
    public List<TrafficDailyEntry> findRecent(int requestedDays) {
        int days = Math.max(1, Math.min(90, requestedDays));
        LocalDate startDate = LocalDate.now(businessClock).minusDays(days - 1L);
        return mapper.findRecent(startDate);
    }

    @Transactional
    public TrafficDailyEntry upsert(LocalDate statDate, TrafficDailyUpsertRequest request) {
        if (statDate.isAfter(LocalDate.now(businessClock))) {
            throw new BusinessException(HttpStatus.CONFLICT, "不能录入未来日期的客流数据");
        }
        if (request.uniqueVisitors() > request.visits()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "独立访客数不能大于到访人数");
        }

        var actor = currentUserService.current();
        String notes = normalizeNotes(request.notes());
        TrafficDailyEntry existing = mapper.findByStatDate(statDate);
        if (existing == null) {
            if (request.expectedVersion() != null) {
                throw stale();
            }
            try {
                mapper.insert(statDate, request.visits(), request.uniqueVisitors(), notes, actor.id());
            } catch (DataIntegrityViolationException exception) {
                throw stale();
            }
        } else {
            if (request.expectedVersion() == null || !existing.version().equals(request.expectedVersion())) {
                throw stale();
            }
            if (mapper.update(statDate, request.visits(), request.uniqueVisitors(), notes,
                    actor.id(), request.expectedVersion()) == 0) {
                throw stale();
            }
        }

        return mapper.findByStatDate(statDate);
    }

    private BusinessException stale() {
        return new BusinessException(HttpStatus.CONFLICT, STALE_MESSAGE);
    }

    private String normalizeNotes(String notes) {
        if (notes == null) return null;
        String normalized = notes.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
