package com.ryan.logisticexpress.address.service;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.address.dto.CreateAddressRequest;
import com.ryan.logisticexpress.address.dto.NormalizedAddressResult;
import com.ryan.logisticexpress.address.entity.Address;
import com.ryan.logisticexpress.address.entity.Province;
import com.ryan.logisticexpress.address.entity.Ward;
import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import com.ryan.logisticexpress.address.repository.AddressRepository;
import com.ryan.logisticexpress.address.repository.ProvinceRepository;
import com.ryan.logisticexpress.address.repository.WardRepository;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final AddressNormalizerService normalizerService;

    @Transactional
    public AddressResponse createOrNormalizeAddress(CreateAddressRequest request) {
        NormalizedAddressResult norm = normalizerService.normalize(request.rawText());

        Province province = null;
        String provinceCode = request.provinceCode() != null ? request.provinceCode() : norm.newProvinceCode();
        if (provinceCode != null) {
            province = provinceRepository.findById(provinceCode).orElse(null);
        }

        Ward ward = null;
        String wardCode = request.wardCode() != null ? request.wardCode() : norm.newWardCode();
        if (wardCode != null) {
            ward = wardRepository.findById(wardCode).orElse(null);
        }

        Address address = Address.builder()
                .rawText(request.rawText().trim())
                .streetDetail(request.streetDetail() != null ? request.streetDetail().trim() : norm.streetDetail())
                .province(province)
                .ward(ward)
                .lat(request.lat() != null ? request.lat() : norm.lat())
                .lng(request.lng() != null ? request.lng() : norm.lng())
                .matchScore(norm.matchScore())
                .normalizationStatus(norm.status())
                .build();

        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getManualReviewQueue() {
        return addressRepository.findByNormalizationStatusOrderByCreatedAtDesc(NormalizationStatus.MANUAL_REVIEW_QUEUE)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse verifyAddress(UUID addressId, String provinceCode, String wardCode, UUID verifiedByUserId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("ADDRESS_NOT_FOUND", "No address found"));

        Province province = provinceRepository.findById(provinceCode)
                .orElseThrow(() -> new ResourceNotFoundException("PROVINCE_NOT_FOUND", "Province/City not found"));

        Ward ward = wardRepository.findById(wardCode)
                .orElseThrow(() -> new ResourceNotFoundException("WARD_NOT_FOUND", "Ward/Commune not found"));

        address.setProvince(province);
        address.setWard(ward);
        address.setNormalizationStatus(NormalizationStatus.VERIFIED);
        address.setVerifiedBy(verifiedByUserId);

        return AddressResponse.from(addressRepository.save(address));
    }
}