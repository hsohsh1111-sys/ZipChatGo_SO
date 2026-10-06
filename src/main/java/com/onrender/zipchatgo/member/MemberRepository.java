package com.onrender.zipchatgo.member;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends CrudRepository<Member, Long> {

    @Query("SELECT * FROM member WHERE email = :email")
    Optional<Member> findByEmail(@Param("email") String email);

    @Query("SELECT COUNT(*) > 0 FROM member WHERE email = :email")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT * FROM member WHERE provider = :provider AND provider_id = :providerId")
    Optional<Member> findByProviderAndProviderId(@Param("provider") String provider,
    @Param("providerId") String providerId);

    // ---- 회원 탈퇴 시 함께 삭제할 종속 데이터 (member_id 로 연결된 테이블)
    // 탈퇴 시 Supabase 에서 지울 파일 경로 조회 (DB 삭제 전에 호출)
    @Query("SELECT file_path FROM property_photo WHERE property_id IN (SELECT id FROM member_property WHERE member_id = :memberId)")
    List<String> findPhotoPaths(@Param("memberId") Long memberId);

    @Query("SELECT file_path FROM property_document WHERE property_id IN (SELECT id FROM member_property WHERE member_id = :memberId)")
    List<String> findDocumentPaths(@Param("memberId") Long memberId);

    // 등록 매물에 딸린 데이터 (member_property.id 를 property_id 로 참조) - 매물보다 먼저 삭제
    @Modifying
    @Query("DELETE FROM property_photo WHERE property_id IN (SELECT id FROM member_property WHERE member_id = :memberId)")
    void deletePropertyPhotos(@Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM property_document WHERE property_id IN (SELECT id FROM member_property WHERE member_id = :memberId)")
    void deletePropertyDocuments(@Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM property_attribute WHERE property_id IN (SELECT id FROM member_property WHERE member_id = :memberId)")
    void deletePropertyAttributes(@Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM member_property WHERE member_id = :memberId")
    void deleteMemberProperties(@Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM inquiry WHERE member_id = :memberId")
    void deleteInquiries(@Param("memberId") Long memberId);
}
