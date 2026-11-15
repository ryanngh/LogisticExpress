package com.ryan.logisticexpress.address;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.address.dto.CreateAddressRequest;
import com.ryan.logisticexpress.address.dto.NormalizeAddressRequest;
import com.ryan.logisticexpress.address.dto.NormalizedAddressResult;
import com.ryan.logisticexpress.address.entity.Province;
import com.ryan.logisticexpress.address.entity.Ward;
import com.ryan.logisticexpress.address.repository.ProvinceRepository;
import com.ryan.logisticexpress.address.repository.WardRepository;
import com.ryan.logisticexpress.address.service.AddressNormalizerService;
import com.ryan.logisticexpress.address.service.AddressService;
import com.ryan.logisticexpress.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressNormalizerService normalizerService;
    private final AddressService addressService;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    /**
     * Nhận địa chỉ thô do người dùng nhập vào và chuyển địa chỉ đó thành dạng chuẩn hóa
     */
    @PostMapping("/normalize")
    public ResponseEntity<NormalizedAddressResult> normalize(@Valid @RequestBody NormalizeAddressRequest request) {
        NormalizedAddressResult result = normalizerService.normalize(request.rawAddress());
        return ResponseEntity.ok(result);
    }

    /**
     * Tiếp nhận thông tin địa chỉ từ người dùng, sau đó xử lý tạo địa chỉ mới hoặc chuẩn hóa địa chỉ
     */
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody CreateAddressRequest request) {
        AddressResponse response = addressService.createOrNormalizeAddress(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/provinces")
    public ResponseEntity<List<Province>> listProvinces() {
        return ResponseEntity.ok(provinceRepository.findAllByOrderByCodeAsc());
    }

    @GetMapping("/provinces/{provinceCode}/wards")
    public ResponseEntity<List<Ward>> listWards(@PathVariable String provinceCode) {
        return ResponseEntity.ok(wardRepository.findByProvinceCodeOrderByNameAsc(provinceCode));
    }

    @GetMapping("/manual-review")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN', 'HUB_STAFF')")
    public ResponseEntity<List<AddressResponse>> getManualReviewQueue() {
        return ResponseEntity.ok(addressService.getManualReviewQueue());
    }
    /**
    *Cho phép nhân viên có quyền xác minh lại địa chỉ chưa được hệ thống xác định chính xác bằng cách chọn tỉnh/thành phố và phường/xã phù hợp
    */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ResponseEntity<AddressResponse> verifyAddress(
            @PathVariable UUID id,
            @RequestParam String provinceCode,
            @RequestParam String wardCode,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID verifiedBy = userDetails != null ? userDetails.getId() : null;
        AddressResponse response = addressService.verifyAddress(id, provinceCode, wardCode, verifiedBy);
        return ResponseEntity.ok(response);
    }
}