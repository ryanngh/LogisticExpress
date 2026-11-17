package com.ryan.logisticexpress.address.repository;

import com.ryan.logisticexpress.address.entity.AdministrativeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AdministrativeMappingRepository extends JpaRepository<AdministrativeMapping, UUID> {

    interface ScoredMapping {
        UUID getId();
        String getLegacyProvinceName();
        String getLegacyDistrictName();
        String getLegacyWardName();
        String getAliasKeywords();
        String getNewProvinceCode();
        String getNewProvinceName();
        String getNewWardCode();
        String getNewWardName();
        UUID getAssignedHubId();
        BigDecimal getDefaultLat();
        BigDecimal getDefaultLng();
        Double getSimilarityScore();
    }

    @SuppressWarnings("SqlResolve")
    @Query(value = """
        SELECT m.id AS id,
               m.legacy_province_name AS legacyProvinceName,
               m.legacy_district_name AS legacyDistrictName,
               m.legacy_ward_name AS legacyWardName,
               m.alias_keywords AS aliasKeywords,
               m.new_province_code AS newProvinceCode,
               p.name AS newProvinceName,
               m.new_ward_code AS newWardCode,
               w.name AS newWardName,
               m.assigned_hub_id AS assignedHubId,
               m.default_lat AS defaultLat,
               m.default_lng AS defaultLng,
               similarity(
                   unaccent(CONCAT_WS(' ', m.legacy_ward_name, m.legacy_district_name, m.legacy_province_name, m.alias_keywords)),
                   unaccent(:cleanText)
               ) AS similarityScore
        FROM administrative_mappings m
        JOIN provinces p ON m.new_province_code = p.code
        JOIN wards w ON m.new_ward_code = w.code
        WHERE similarity(
                   unaccent(CONCAT_WS(' ', m.legacy_ward_name, m.legacy_district_name, m.legacy_province_name, m.alias_keywords)),
                   unaccent(:cleanText)
               ) >= :threshold
        ORDER BY similarityScore DESC
        LIMIT 5
        """, nativeQuery = true)
    List<ScoredMapping> findTopFuzzyMatches(@Param("cleanText") String cleanText, @Param("threshold") double threshold);
}
