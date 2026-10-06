package com.onrender.zipchatgo.member;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[0-9]{8,9}$");

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupabaseStorageCleaner storageCleaner;

    /**
     * 회원가입 (일반회원 전용, 이번 작업 범위)
     */
    public Member signup(String email, String rawPassword, String name, String phone) {
        if (email == null || email.isBlank()
                || rawPassword == null || rawPassword.isBlank()
                || name == null || name.isBlank()) {
            throw new IllegalStateException("이메일, 비밀번호, 이름을 모두 입력해주세요.");
        }

        email = email.trim();
        name = name.trim();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalStateException("올바른 이메일 형식을 입력해주세요.");
        }

        if (rawPassword.length() < 8) {
            throw new IllegalStateException("비밀번호는 8자 이상이어야 해요.");
        }

        // 휴대폰번호는 선택: 비어 있으면 null, 값이 있으면 숫자만 남겨 형식 검사 후 저장
        String normalizedPhone = null;
        if (phone != null && !phone.isBlank()) {
            normalizedPhone = phone.replaceAll("[^0-9]", "");
            if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                throw new IllegalStateException("휴대폰번호 형식을 확인해주세요.");
            }
        }

        if (memberRepository.existsByEmail(email)) {
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }

        Member member = new Member();
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(rawPassword));
        member.setName(name);
        member.setPhone(normalizedPhone);
        member.setMemberType("GENERAL");

        return memberRepository.save(member);
    }

    /**
     * 로그인 검증 - 성공 시 Member 반환, 실패 시 예외 발생
     */
    public Member login(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 이메일입니다."));

        if (member.getPassword() == null) {
            throw new IllegalStateException("소셜 로그인으로 가입된 계정이에요. 소셜 로그인을 이용해주세요.");
        }

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new IllegalStateException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        return member;
    }

    /**
     * 회원 조회 - 없으면 예외
     */
    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 회원입니다."));
    }

    /**
     * 회원 탈퇴 (완전 삭제)
     * - 일반회원: 비밀번호 재확인
     * - 소셜회원(password 없음): 확인 문구 "탈퇴" 입력
     * 종속 데이터(매물 사진/서류/속성, member_property, inquiry)를 먼저 지운 뒤 회원 행을 삭제한다.
     */
    @Transactional
    public void withdraw(Long memberId, String rawPassword, String confirmText) {
        Member member = getMember(memberId);

        if (member.getPassword() != null) {
            if (rawPassword == null || rawPassword.isBlank()
                    || !passwordEncoder.matches(rawPassword, member.getPassword())) {
                throw new IllegalStateException("비밀번호가 일치하지 않습니다.");
            }
        } else if (confirmText == null || !"탈퇴".equals(confirmText.trim())) {
            throw new IllegalStateException("확인 문구로 '탈퇴'를 입력해주세요.");
        }

        // Supabase 에서 지울 파일 경로를 DB 삭제 전에 미리 확보
        List<String> filePaths = new ArrayList<>();
        filePaths.addAll(memberRepository.findPhotoPaths(memberId));
        filePaths.addAll(memberRepository.findDocumentPaths(memberId));

        // 매물에 딸린 사진/서류/속성 -> 매물 -> 문의 -> 회원 순서로 삭제
        memberRepository.deletePropertyPhotos(memberId);
        memberRepository.deletePropertyDocuments(memberId);
        memberRepository.deletePropertyAttributes(memberId);
        memberRepository.deleteMemberProperties(memberId);
        memberRepository.deleteInquiries(memberId);
        memberRepository.deleteById(memberId);

        // DB 삭제가 커밋된 뒤에만 파일 삭제 (롤백되면 파일은 건드리지 않음, 파일 삭제 실패는 탈퇴에 영향 없음)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageCleaner.deleteAll(filePaths);
            }
        });
    }
}
