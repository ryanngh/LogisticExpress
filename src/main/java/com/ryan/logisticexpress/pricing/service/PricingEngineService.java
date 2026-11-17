package com.ryan.logisticexpress.pricing.service;

import com.ryan.logisticexpress.common.exception.ConflictException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.pricing.dto.ServiceTypeRequest;
import com.ryan.logisticexpress.pricing.dto.ServiceTypeResponse;
import com.ryan.logisticexpress.pricing.entity.ServiceType;
import com.ryan.logisticexpress.pricing.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingEngineService {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> listServiceTypes() {
        return serviceTypeRepository.findAllByOrderByCodeAsc().stream()
                .map(ServiceTypeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceTypeResponse getServiceTypeById(UUID id) {
        ServiceType st = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SERVICE_TYPE_NOT_FOUND", "Service type not found: " + id));
        return ServiceTypeResponse.from(st);
    }

    @Transactional
    public ServiceTypeResponse createServiceType(ServiceTypeRequest request) {
        String code = request.code().trim().toUpperCase();
        if (serviceTypeRepository.existsByCode(code)) {
            throw new ConflictException("SERVICE_TYPE_EXISTS", "Service type already exists: " + code);
        }
        ServiceType st = ServiceType.builder()
                .code(code)
                .name(request.name().trim())
                .volumetricDivisor(request.volumetricDivisor())
                .slaHours(request.slaHours())
                .build();
        return ServiceTypeResponse.from(serviceTypeRepository.save(st));
    }

    @Transactional
    public ServiceTypeResponse updateServiceType(UUID id, ServiceTypeRequest request) {
        ServiceType st = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SERVICE_TYPE_NOT_FOUND", "Service type not found: " + id));
        String code = request.code().trim().toUpperCase();
        serviceTypeRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("SERVICE_TYPE_EXISTS", "Service type already exists: " + code);
                });
        st.setCode(code);
        st.setName(request.name().trim());
        st.setVolumetricDivisor(request.volumetricDivisor());
        st.setSlaHours(request.slaHours());
        return ServiceTypeResponse.from(serviceTypeRepository.save(st));
    }
}
