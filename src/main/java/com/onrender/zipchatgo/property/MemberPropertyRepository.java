package com.onrender.zipchatgo.property;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface MemberPropertyRepository extends CrudRepository<MemberProperty, Long> {

    List<MemberProperty> findByStatus(String status);

    // 내 매물 목록 (최신 등록순)
    List<MemberProperty> findByMemberIdOrderByIdDesc(Long memberId);
}
