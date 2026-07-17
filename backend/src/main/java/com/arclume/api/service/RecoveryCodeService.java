package com.arclume.api.service;

import com.arclume.api.domain.RecoveryCode;
import com.arclume.api.domain.User;
import com.arclume.api.repository.RecoveryCodeRepository;
import com.arclume.api.security.TokenHashService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RecoveryCodeService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_COUNT = 10;
    private static final int CODE_CHARACTERS = 12;

    private final RecoveryCodeRepository recoveryCodeRepository;
    private final TokenHashService tokenHashService;

    public RecoveryCodeService(RecoveryCodeRepository recoveryCodeRepository,
                               TokenHashService tokenHashService) {
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.tokenHashService = tokenHashService;
    }

    @Transactional
    public List<String> replaceCodes(User user) {
        recoveryCodeRepository.deleteByUserId(user.getId());
        List<String> rawCodes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String rawCode = generateCode();
            RecoveryCode recoveryCode = new RecoveryCode();
            recoveryCode.setUser(user);
            recoveryCode.setCodeHash(tokenHashService.hash(normalize(rawCode)));
            recoveryCodeRepository.save(recoveryCode);
            rawCodes.add(rawCode);
        }
        return rawCodes;
    }

    @Transactional
    public boolean consume(User user, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return false;
        }
        return recoveryCodeRepository
                .findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), tokenHashService.hash(normalize(rawCode)))
                .map(code -> {
                    code.setUsedAt(Instant.now());
                    return true;
                })
                .orElse(false);
    }

    private String generateCode() {
        byte[] random = tokenHashService.randomBytes(CODE_CHARACTERS);
        StringBuilder value = new StringBuilder(CODE_CHARACTERS + 2);
        for (int i = 0; i < random.length; i++) {
            if (i > 0 && i % 4 == 0) {
                value.append('-');
            }
            value.append(ALPHABET.charAt(random[i] & 31));
        }
        return value.toString();
    }

    private String normalize(String value) {
        return value.replace("-", "")
                .replace(" ", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
