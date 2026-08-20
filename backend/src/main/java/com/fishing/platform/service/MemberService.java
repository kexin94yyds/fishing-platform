package com.fishing.platform.service;

import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Member;
import com.fishing.platform.domain.DomainModels.MemberActivity;
import com.fishing.platform.domain.DomainModels.MemberAudit;
import com.fishing.platform.dto.ApiDtos.MemberRequest;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.CatchMapper;
import com.fishing.platform.mapper.MemberMapper;
import com.fishing.platform.mapper.SalesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberService {
    private final MemberMapper mapper;
    private final BookingMapper bookingMapper;
    private final CatchMapper catchMapper;
    private final SalesMapper salesMapper;
    private final CurrentUserService currentUserService;

    public MemberService(MemberMapper mapper,
                         BookingMapper bookingMapper,
                         CatchMapper catchMapper,
                         SalesMapper salesMapper,
                         CurrentUserService currentUserService) {
        this.mapper = mapper;
        this.bookingMapper = bookingMapper;
        this.catchMapper = catchMapper;
        this.salesMapper = salesMapper;
        this.currentUserService = currentUserService;
    }

    public List<Member> findAll() {
        return mapper.findAll();
    }

    public MemberActivity activity(Long id) {
        Member member = mapper.findById(id);
        if (member == null) {
            throw new NotFoundException("会员不存在");
        }
        return new MemberActivity(
                member,
                bookingMapper.findAll(null, null, id),
                catchMapper.findAll(null, null, null, id),
                salesMapper.findByMemberId(id));
    }

    public List<MemberAudit> audits(Long id, int requestedLimit) {
        if (mapper.findById(id) == null) {
            throw new NotFoundException("会员不存在");
        }
        return mapper.findAudits(id, Math.max(1, Math.min(requestedLimit, 100)));
    }

    @Transactional
    public Member create(MemberRequest request) {
        String memberNo = request.memberNo() == null || request.memberNo().isBlank()
                ? BusinessNumbers.next("M")
                : request.memberNo().trim();
        mapper.insert(memberNo, request.name().trim(), request.phone().trim(),
                request.level() == null ? "NORMAL" : request.level(),
                request.points() == null ? 0 : request.points(),
                request.status() == null ? "ACTIVE" : request.status());
        Member created = mapper.findByNo(memberNo);
        var actor = currentUserService.current();
        mapper.insertAudit(actor.id(), actor.username(), created.id(), created.memberNo(), "CREATE",
                null, created.level(), null, created.points(), null, created.status());
        return created;
    }

    @Transactional
    public Member update(Long id, MemberRequest request) {
        Member existing = mapper.findByIdForUpdate(id);
        if (existing == null) {
            throw new NotFoundException("会员不存在");
        }
        String memberNo = request.memberNo() == null || request.memberNo().isBlank()
                ? existing.memberNo()
                : request.memberNo().trim();
        int changed = mapper.update(id, memberNo, request.name().trim(), request.phone().trim(),
                request.level() == null ? existing.level() : request.level(),
                request.points() == null ? existing.points() : request.points(),
                request.status() == null ? existing.status() : request.status());
        if (changed == 0) {
            throw new NotFoundException("会员不存在");
        }
        Member updated = mapper.findById(id);
        var actor = currentUserService.current();
        mapper.insertAudit(actor.id(), actor.username(), updated.id(), updated.memberNo(), "UPDATE",
                existing.level(), updated.level(), existing.points(), updated.points(),
                existing.status(), updated.status());
        return updated;
    }
}
