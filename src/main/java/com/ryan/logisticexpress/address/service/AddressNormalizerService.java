package com.ryan.logisticexpress.address.service;

import com.ryan.logisticexpress.address.dto.NormalizedAddressResult;
import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import com.ryan.logisticexpress.address.repository.AdministrativeMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressNormalizerService {

    private final AdministrativeMappingRepository mappingRepository;

    private static final double AUTO_VERIFY_THRESHOLD = 0.85;
    private static final double SUGGESTION_THRESHOLD = 0.50;

    /**
     * 3-step address normalization algorithm
     */
    @Transactional(readOnly = true)
    public NormalizedAddressResult normalize(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return new NormalizedAddressResult(
                    "", "", "", null, null, null, null, null, null, null,
                    BigDecimal.ZERO, NormalizationStatus.UNRESOLVED
            );
        }

        // 1: Text Normalization
        String cleanedText = preprocessText(rawInput);

        // 2: Fuzzy search query with PostgreSQL pg_trgm
        List<AdministrativeMappingRepository.ScoredMapping> matches =
                mappingRepository.findTopFuzzyMatches(cleanedText, 0.35);

        if (matches == null || matches.isEmpty()) {
            log.debug("No mapping matches found for address: raw='{}', cleaned='{}'", rawInput, cleanedText);
            return new NormalizedAddressResult(
                    rawInput, cleanedText, extractStreetDetail(rawInput, null, null, null),
                    null, null, null, null, null, null, null,
                    BigDecimal.ZERO, NormalizationStatus.UNRESOLVED
            );
        }

        // 3: Top-scoring result and thresholding
        AdministrativeMappingRepository.ScoredMapping topMatch = matches.getFirst();
        double score = topMatch.getSimilarityScore() != null ? topMatch.getSimilarityScore() : 0.0;
        BigDecimal matchScore = BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);

        NormalizationStatus status;
        if (score >= AUTO_VERIFY_THRESHOLD) {
            status = NormalizationStatus.VERIFIED;
        } else if (score >= SUGGESTION_THRESHOLD) {
            status = NormalizationStatus.SUGGESTED;
        } else {
            status = NormalizationStatus.UNRESOLVED;
        }

        String streetDetail = extractStreetDetail(
                rawInput,
                topMatch.getLegacyWardName(),
                topMatch.getLegacyDistrictName(),
                topMatch.getLegacyProvinceName()
        );

        log.info("Normalized address: '{}' -> ward='{}', province='{}', score={}, status={}",
                rawInput, topMatch.getNewWardName(), topMatch.getNewProvinceName(), matchScore, status);

        return new NormalizedAddressResult(
                rawInput,
                cleanedText,
                streetDetail,
                topMatch.getNewProvinceCode(),
                topMatch.getNewProvinceName(),
                topMatch.getNewWardCode(),
                topMatch.getNewWardName(),
                topMatch.getAssignedHubId(),
                topMatch.getDefaultLat(),
                topMatch.getDefaultLng(),
                matchScore,
                status
        );
    }

    public String preprocessText(String input) {
        if (input == null) return "";

        String text = Normalizer.normalize(input, Normalizer.Form.NFC).toLowerCase().trim();
        text = text.replaceAll("[,;\\-_/]+", " ");

        text = Pattern.compile("\\b(tp\\.|tp |t\\.p|city)\\b").matcher(text).replaceAll("city ");
        text = Pattern.compile("\\b(tx\\.|tx |t\\.x|town)\\b").matcher(text).replaceAll( "commune ");
        text = Pattern.compile("\\b(tt\\.|tt |t\\.t|township)\\b").matcher(text).replaceAll("town ");
        text = Pattern.compile("\\b(h\\.|h )\\b").matcher(text).replaceAll("district ");
        text = Pattern.compile("\\b(q\\.|q |q)([0-9]{1,2})\\b").matcher(text).replaceAll("district $2 ");
        text = Pattern.compile("\\b(q\\.|q )\\b").matcher(text).replaceAll("district ");
        text = Pattern.compile("\\b(p\\.|p |p)([0-9]{1,2})\\b").matcher(text).replaceAll("ward $2 ");
        text = Pattern.compile("\\b(p\\.|p )\\b").matcher(text).replaceAll("ward ");

        text = Pattern.compile("\\b(hcm|sg|tphcm)\\b").matcher(text).replaceAll("Ho Chi Minh city");
        text = Pattern.compile("\\b(hn|tphn)\\b").matcher(text).replaceAll("Ha Noi capital");
        text = Pattern.compile("\\b(đn|danang)\\b").matcher(text).replaceAll("Da Nang");

        return text.replaceAll("\\s+", " ").trim();
    }

    private String extractStreetDetail(String rawText, String ward, String district, String province) {
        if (rawText == null) return "";
        String result = rawText;
        if (ward != null && !ward.isBlank()) {
            result = result.replaceAll("(?i)" + Pattern.quote(ward), "");
        }
        if (district != null && !district.isBlank()) {
            result = result.replaceAll("(?i)" + Pattern.quote(district), "");
        }
        if (province != null && !province.isBlank()) {
            result = result.replaceAll("(?i)" + Pattern.quote(province), "");
        }
        return result.replaceAll("[,\\-\\s]+", " ").trim();
    }
}
