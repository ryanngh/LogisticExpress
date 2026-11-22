package com.ryan.logisticexpress.address.service;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.address.dto.CreateVerifiedAddressRequest;
import com.ryan.logisticexpress.address.dto.ProvinceResponse;
import com.ryan.logisticexpress.address.dto.WardResponse;
import com.ryan.logisticexpress.address.entity.Address;
import com.ryan.logisticexpress.address.entity.Province;
import com.ryan.logisticexpress.address.entity.Ward;
import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import com.ryan.logisticexpress.address.repository.AddressRepository;
import com.ryan.logisticexpress.address.repository.ProvinceRepository;
import com.ryan.logisticexpress.address.repository.WardRepository;
import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {

    public static final String CACHE_PROVINCES = "address:provinces";
    public static final String CACHE_WARDS_BY_PROVINCE = "address:wards:province";

    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final AddressRepository addressRepository;

    @Cacheable(value = CACHE_PROVINCES, key = "'all'")
    @Transactional(readOnly = true)
    public List<ProvinceResponse> listProvinces() {
        return provinceRepository.findAllByOrderByCodeAsc().stream()
                .map(ProvinceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProvinceResponse getProvinceResponse(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("INVALID_PROVINCE", "Province code cannot be blank");
        }
        return provinceRepository.findById(code.trim())
                .map(ProvinceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("PROVINCE_NOT_FOUND", "Province not found: " + code));
    }

    @Cacheable(value = CACHE_WARDS_BY_PROVINCE, key = "#provinceCode")
    @Transactional(readOnly = true)
    public List<WardResponse> listWardsByProvince(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            throw new BadRequestException("INVALID_PROVINCE", "Province code cannot be blank");
        }
        String code = provinceCode.trim();
        if (!provinceRepository.existsById(code)) {
            throw new ResourceNotFoundException("PROVINCE_NOT_FOUND", "Province not found: " + code);
        }
        return wardRepository.findByProvince_CodeOrderByCodeAsc(code).stream()
                .map(WardResponse::from)
                .toList();
    }

    @CacheEvict(value = CACHE_WARDS_BY_PROVINCE, allEntries = true)
    @Transactional
    public List<WardResponse> assignHubToWards(UUID hubId, List<String> wardCodes) {
        if (wardCodes == null || wardCodes.isEmpty()) {
            return List.of();
        }
        List<String> normalizedCodes = wardCodes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<Ward> wards = wardRepository.findAllById(normalizedCodes);
        if (wards.size() != normalizedCodes.size()) {
            throw new ResourceNotFoundException("WARD_NOT_FOUND", "One or more ward codes were not found");
        }
        for (Ward ward : wards) {
            ward.setAssignedHubId(hubId);
        }
        return wardRepository.saveAll(wards).stream()
                .map(WardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WardResponse> listWardsByAssignedHub(UUID hubId) {
        return wardRepository.findByAssignedHubIdOrderByCodeAsc(hubId).stream()
                .map(WardResponse::from)
                .toList();
    }

    @Transactional
    public Address createVerifiedAddressSnapshot(CreateVerifiedAddressRequest request, UUID verifiedByUserId) {
        String provCode = request.provinceCode().trim();
        String wardCode = request.wardCode().trim();
        String street = request.streetDetail().trim();

        Province province = provinceRepository.findById(provCode)
                .orElseThrow(() -> new ResourceNotFoundException("PROVINCE_NOT_FOUND", "Province not found: " + provCode));
        Ward ward = wardRepository.findById(wardCode)
                .orElseThrow(() -> new ResourceNotFoundException("WARD_NOT_FOUND", "Ward not found: " + wardCode));

        if (!ward.getProvince().getCode().equals(province.getCode())) {
            throw new BadRequestException("WARD_PROVINCE_MISMATCH", "Ward does not belong to the specified province");
        }

        BigDecimal lat = request.lat() != null ? request.lat() : ward.getCenterLat();
        BigDecimal lng = request.lng() != null ? request.lng() : ward.getCenterLng();
        String rawText = street + ", " + ward.getName() + ", " + province.getName();

        Address address = Address.builder()
                .rawText(rawText)
                .streetDetail(street)
                .province(province)
                .ward(ward)
                .lat(lat)
                .lng(lng)
                .matchScore(new BigDecimal("1.000"))
                .normalizationStatus(NormalizationStatus.VERIFIED)
                .verifiedBy(verifiedByUserId)
                .build();

        return addressRepository.save(address);
    }

    @Transactional(readOnly = true)
    public Address getAddressOrThrow(UUID addressId) {
        return addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("ADDRESS_NOT_FOUND", "Address not found: " + addressId));
    }

    @Transactional(readOnly = true)
    public AddressResponse getAddressResponse(UUID addressId) {
        if (addressId == null) {
            return null;
        }
        return addressRepository.findById(addressId)
                .map(AddressResponse::from)
                .orElse(null);
    }
}
