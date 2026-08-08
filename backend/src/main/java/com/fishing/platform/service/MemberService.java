package com.fishing.platform.service;

import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Member;
import com.fishing.platform.dto.ApiDtos.MemberRequest;
import com.fishing.platform.mapper.MemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberService {
    private final MemberMapper mapper;

    public MemberService(MemberMapper mapper) {
        this.mapper = mapper;
    }

    public List<Member> findAll() {
        return mapper.findAll();
    }

    public Member create(MemberRequest request) {
        String memberNo = request.memberNo() == null || request.memberNo().isBlank()
                ? BusinessNumbers.next("M")
                : request.memberNo().trim();
        mapper.insert(memberNo, request.name().trim(), request.phone().trim(),
                request.level() == null ? "NORMAL" : request.level(),
                request.points() == null ? 0 : request.points(),
                request.status() == null ? "ACTIVE" : request.status());
        return mapper.findByNo(memberNo);
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
        return mapper.findById(id);
    }
}
