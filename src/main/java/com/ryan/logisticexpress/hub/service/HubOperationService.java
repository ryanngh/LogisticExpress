package com.ryan.logisticexpress.hub.service;

import com.ryan.logisticexpress.address.dto.WardResponse;
import com.ryan.logisticexpress.address.service.AddressService;
import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ConflictException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.hub.dto.HubRequest;
import com.ryan.logisticexpress.hub.dto.HubResponse;
import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.hub.enums.HubLevel;
import com.ryan.logisticexpress.hub.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubOperationService {

    private final HubRepository hubRepository;
    private final AddressService addressService;

    @Transactional(readOnly = true)
    public List<HubResponse> listHubs(HubLevel level) {
        List<Hub> hubs = (level != null)
                ? hubRepository.findByLevelOrderByCodeAsc(level)
                : hubRepository.findAllByOrderByCodeAsc();
        return hubs.stream()
                .map(h -> HubResponse.from(h, getAssignedWardCodes(h.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public HubResponse getHubById(UUID id) {
        Hub hub = getHubOrThrow(id);
        return HubResponse.from(hub, getAssignedWardCodes(id));
    }

    @Transactional(readOnly = true)
    public Hub getHubOrThrow(UUID id) {
        return hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HUB_NOT_FOUND", "Hub not found: " + id));
    }

    @Transactional
    public HubResponse createHub(HubRequest request) {
        String code = request.code().trim().toUpperCase();
        if (hubRepository.existsByCode(code)) {
            throw new ConflictException("HUB_CODE_EXISTS", "Hub code already exists: " + code);
        }

        Hub parentHub = resolveParentHub(null, request.level(), request.parentHubId());

        Hub hub = Hub.builder()
                .code(code)
                .name(request.name().trim())
                .level(request.level())
                .parentHub(parentHub)
                .addressId(request.addressId())
                .lat(request.lat())
                .lng(request.lng())
                .build();

        Hub saved = hubRepository.save(hub);

        if (request.assignedWardCodes() != null && !request.assignedWardCodes().isEmpty()) {
            addressService.assignHubToWards(saved.getId(), request.assignedWardCodes());
        }

        return HubResponse.from(saved, getAssignedWardCodes(saved.getId()));
    }

    @Transactional
    public HubResponse updateHub(UUID id, HubRequest request) {
        Hub hub = getHubOrThrow(id);
        String code = request.code().trim().toUpperCase();

        hubRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("HUB_CODE_EXISTS", "Hub code already exists: " + code);
                });

        Hub parentHub = resolveParentHub(id, request.level(), request.parentHubId());

        hub.setCode(code);
        hub.setName(request.name().trim());
        hub.setLevel(request.level());
        hub.setParentHub(parentHub);
        hub.setAddressId(request.addressId());
        hub.setLat(request.lat());
        hub.setLng(request.lng());

        Hub saved = hubRepository.save(hub);

        if (request.assignedWardCodes() != null) {
            List<String> currentWards = getAssignedWardCodes(id);
            if (!currentWards.isEmpty()) {
                addressService.assignHubToWards(null, currentWards);
            }
            if (!request.assignedWardCodes().isEmpty()) {
                addressService.assignHubToWards(id, request.assignedWardCodes());
            }
        }

        return HubResponse.from(saved, getAssignedWardCodes(id));
    }

    @Transactional
    public HubResponse assignWardsToHub(UUID hubId, List<String> wardCodes) {
        Hub hub = getHubOrThrow(hubId);
        addressService.assignHubToWards(hub.getId(), wardCodes);
        return HubResponse.from(hub, getAssignedWardCodes(hub.getId()));
    }

    @Transactional
    public void deleteHub(UUID id) {
        Hub hub = getHubOrThrow(id);
        if (hubRepository.existsByParentHub_Id(id)) {
            throw new ConflictException("HUB_HAS_CHILDREN", "Cannot delete hub that has child post offices");
        }
        List<String> assignedWards = getAssignedWardCodes(id);
        if (!assignedWards.isEmpty()) {
            addressService.assignHubToWards(null, assignedWards);
        }
        hubRepository.delete(hub);
    }

    private Hub resolveParentHub(UUID selfId, HubLevel level, UUID parentHubId) {
        if (parentHubId == null) {
            return null;
        }
        if (selfId != null && selfId.equals(parentHubId)) {
            throw new BadRequestException("INVALID_PARENT_HUB", "Hub cannot be its own parent");
        }
        Hub parent = getHubOrThrow(parentHubId);
        if (level == HubLevel.LOCAL_POST_OFFICE && parent.getLevel() != HubLevel.REGIONAL_HUB) {
            throw new BadRequestException("INVALID_PARENT_HUB", "Parent of LOCAL_POST_OFFICE must be a REGIONAL_HUB");
        }
        return parent;
    }

    private List<String> getAssignedWardCodes(UUID hubId) {
        return addressService.listWardsByAssignedHub(hubId).stream()
                .map(WardResponse::code)
                .toList();
    }
}
