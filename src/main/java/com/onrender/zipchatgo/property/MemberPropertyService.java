package com.onrender.zipchatgo.property;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberPropertyService {

    private static final int SIGNED_URL_SECONDS = 600; // 사진/서류 열람용 임시 주소 유효시간(10분)

    private final MemberPropertyRepository memberPropertyRepository;
    private final PropertyPhotoRepository propertyPhotoRepository;
    private final PropertyDocumentRepository propertyDocumentRepository;
    private final SupabaseStorageService supabaseStorageService;

    // ===================== 등록 =====================

    public MemberProperty register(Long memberId, PropertyRegisterRequest req) {
        MemberProperty property = new MemberProperty();
        property.setMemberId(memberId);
        applyBasicFields(property, req);
        property.setLatitude(req.getLatitude());
        property.setLongitude(req.getLongitude());
        property.setTransitInfo(req.getTransitInfo());
        property.setSchoolInfo(req.getSchoolInfo());
        property.setStatus("PENDING");
        property.setAttributes(buildAttributes(req));

        return memberPropertyRepository.save(property);
    }

    // ===================== 내 매물 조회 =====================

    /** 내 매물 목록 (카드용 요약 + 대표 사진 임시 주소) */
    public List<Map<String, Object>> listMine(Long memberId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MemberProperty p : memberPropertyRepository.findByMemberIdOrderByIdDesc(memberId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("propertyType", p.getPropertyType());
            item.put("dealType", p.getDealType());
            item.put("address1", p.getAddress1());
            item.put("address2", p.getAddress2());
            item.put("area", p.getArea());
            item.put("price", p.getPrice());
            item.put("deposit", p.getDeposit());
            item.put("monthly", p.getMonthly());
            item.put("status", p.getStatus());
            item.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toString());

            List<PropertyPhoto> photos =
                    propertyPhotoRepository.findByPropertyIdOrderBySortOrderAscIdAsc(p.getId());
            item.put("photoCount", photos.size());
            item.put("thumbnailUrl", photos.isEmpty() ? null
                    : supabaseStorageService.createSignedUrl(photos.get(0).getFilePath(), SIGNED_URL_SECONDS));
            result.add(item);
        }
        return result;
    }

    /** 내 매물 상세 (속성·사진·서류와 각각의 임시 주소 포함). 수정 화면의 초기값으로도 쓴다. */
    public Map<String, Object> detail(Long memberId, Long propertyId) {
        MemberProperty p = getOwned(memberId, propertyId);

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", p.getId());
        d.put("propertyType", p.getPropertyType());
        d.put("dealType", p.getDealType());
        d.put("address1", p.getAddress1());
        d.put("address2", p.getAddress2());
        d.put("latitude", p.getLatitude());
        d.put("longitude", p.getLongitude());
        d.put("area", p.getArea());
        d.put("floorInfo", p.getFloorInfo());
        d.put("rooms", p.getRooms());
        d.put("baths", p.getBaths());
        d.put("maintenanceFee", p.getMaintenanceFee());
        d.put("etcFee", p.getEtcFee());
        d.put("transitInfo", p.getTransitInfo());
        d.put("schoolInfo", p.getSchoolInfo());
        d.put("price", p.getPrice());
        d.put("deposit", p.getDeposit());
        d.put("monthly", p.getMonthly());
        d.put("ownerName", p.getOwnerName());
        d.put("ownerPhone", p.getOwnerPhone());
        d.put("status", p.getStatus());
        d.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toString());

        List<String> tags = new ArrayList<>();
        List<String> options = new ArrayList<>();
        List<String> utilities = new ArrayList<>();
        if (p.getAttributes() != null) {
            for (PropertyAttribute a : p.getAttributes()) {
                if ("TAG".equals(a.getCategory())) tags.add(a.getValue());
                else if ("OPTION".equals(a.getCategory())) options.add(a.getValue());
                else if ("UTILITY".equals(a.getCategory())) utilities.add(a.getValue());
            }
        }
        d.put("tags", tags);
        d.put("options", options);
        d.put("utilities", utilities);

        List<Map<String, Object>> photos = new ArrayList<>();
        for (PropertyPhoto ph : propertyPhotoRepository.findByPropertyIdOrderBySortOrderAscIdAsc(propertyId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ph.getId());
            m.put("originalName", ph.getOriginalName());
            m.put("sortOrder", ph.getSortOrder());
            m.put("url", supabaseStorageService.createSignedUrl(ph.getFilePath(), SIGNED_URL_SECONDS));
            photos.add(m);
        }
        d.put("photos", photos);

        List<Map<String, Object>> documents = new ArrayList<>();
        for (PropertyDocument doc : propertyDocumentRepository.findByPropertyId(propertyId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", doc.getId());
            m.put("docType", doc.getDocType());
            m.put("originalName", doc.getOriginalName());
            m.put("url", supabaseStorageService.createSignedUrl(doc.getFilePath(), SIGNED_URL_SECONDS));
            documents.add(m);
        }
        d.put("documents", documents);

        return d;
    }

    // ===================== 수정 =====================

    /**
     * 매물 수정. 저장과 동시에 상태를 PENDING(재검토)으로 되돌린다.
     * 좌표/교통/학군 값은 요청에 들어있을 때만 덮어쓴다(수정 화면에서 위치를 다시 확인하지 않은 경우 기존 값 유지).
     */
    @Transactional
    public MemberProperty update(Long memberId, Long propertyId, PropertyRegisterRequest req) {
        MemberProperty p = getOwned(memberId, propertyId);

        if (req.getAddress1() == null || req.getAddress1().isBlank()) {
            throw new IllegalStateException("주소를 입력해주세요.");
        }

        applyBasicFields(p, req);
        if (req.getLatitude() != null && req.getLongitude() != null) {
            p.setLatitude(req.getLatitude());
            p.setLongitude(req.getLongitude());
        }
        if (req.getTransitInfo() != null) p.setTransitInfo(req.getTransitInfo());
        if (req.getSchoolInfo() != null) p.setSchoolInfo(req.getSchoolInfo());

        p.setAttributes(buildAttributes(req));
        p.setStatus("PENDING");

        return memberPropertyRepository.save(p);
    }

    // ===================== 삭제 =====================

    /** 매물 한 건 삭제: 사진·서류 -> 매물(속성 포함) 순서로 지우고, 커밋 후 Supabase 파일 삭제 */
    @Transactional
    public void delete(Long memberId, Long propertyId) {
        MemberProperty p = getOwned(memberId, propertyId);

        List<PropertyPhoto> photos = propertyPhotoRepository.findByPropertyIdOrderByIdAsc(propertyId);
        List<PropertyDocument> docs = propertyDocumentRepository.findByPropertyId(propertyId);

        List<String> paths = new ArrayList<>();
        for (PropertyPhoto ph : photos) paths.add(ph.getFilePath());
        for (PropertyDocument doc : docs) paths.add(doc.getFilePath());

        propertyPhotoRepository.deleteAll(photos);
        propertyDocumentRepository.deleteAll(docs);
        memberPropertyRepository.delete(p);

        deleteFilesAfterCommit(paths);
    }

    /** 사진 한 장 삭제 */
    @Transactional
    public void deletePhoto(Long memberId, Long propertyId, Long photoId) {
        getOwned(memberId, propertyId);

        PropertyPhoto photo = propertyPhotoRepository.findById(photoId)
                .filter(ph -> propertyId.equals(ph.getPropertyId()))
                .orElseThrow(() -> new IllegalStateException("사진을 찾을 수 없습니다."));

        propertyPhotoRepository.delete(photo);
        deleteFilesAfterCommit(List.of(photo.getFilePath()));
    }

    /** 서류 한 건 삭제 (소유 증빙이 바뀌는 것이므로 재검토 상태로 되돌린다) */
    @Transactional
    public void deleteDocument(Long memberId, Long propertyId, Long documentId) {
        MemberProperty p = getOwned(memberId, propertyId);

        PropertyDocument doc = propertyDocumentRepository.findById(documentId)
                .filter(d -> propertyId.equals(d.getPropertyId()))
                .orElseThrow(() -> new IllegalStateException("서류를 찾을 수 없습니다."));

        propertyDocumentRepository.delete(doc);
        p.setStatus("PENDING");
        memberPropertyRepository.save(p);

        deleteFilesAfterCommit(List.of(doc.getFilePath()));
    }

    // ===================== 내부 공통 =====================

    /** 매물이 없거나 내 매물이 아니면 똑같이 "찾을 수 없음"으로 처리 (남의 매물 존재 여부를 알려주지 않음) */
    private MemberProperty getOwned(Long memberId, Long propertyId) {
        return memberPropertyRepository.findById(propertyId)
                .filter(p -> memberId.equals(p.getMemberId()))
                .orElseThrow(() -> new IllegalStateException("매물을 찾을 수 없습니다."));
    }

    private void applyBasicFields(MemberProperty p, PropertyRegisterRequest req) {
        p.setPropertyType(req.getPropertyType());
        p.setDealType(req.getDealType());
        p.setAddress1(req.getAddress1());
        p.setAddress2(req.getAddress2());
        p.setArea(req.getArea());
        p.setFloorInfo(req.getFloorInfo());
        p.setRooms(req.getRooms());
        p.setBaths(req.getBaths());
        p.setMaintenanceFee(req.getMaintenanceFee());
        p.setEtcFee(req.getEtcFee());
        p.setPrice(req.getPrice());
        p.setDeposit(req.getDeposit());
        p.setMonthly(req.getMonthly());
        p.setOwnerName(req.getOwnerName());
        p.setOwnerPhone(req.getOwnerPhone());
    }

    private Set<PropertyAttribute> buildAttributes(PropertyRegisterRequest req) {
        Set<PropertyAttribute> attributes = new HashSet<>();
        addAttributes(attributes, "TAG", req.getTags());
        addAttributes(attributes, "OPTION", req.getOptions());
        addAttributes(attributes, "UTILITY", req.getUtilities());
        return attributes;
    }

    private void addAttributes(Set<PropertyAttribute> target, String category, List<String> values) {
        if (values == null) return;
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            target.add(new PropertyAttribute(null, category, v));
        }
    }

    /** DB 삭제가 커밋된 뒤에만 Supabase 파일을 지운다 (롤백되면 파일은 그대로, 파일 삭제 실패는 무시) */
    private void deleteFilesAfterCommit(List<String> paths) {
        if (paths == null || paths.isEmpty()) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    supabaseStorageService.deleteObjects(paths);
                }
            });
        } else {
            supabaseStorageService.deleteObjects(paths);
        }
    }
}
