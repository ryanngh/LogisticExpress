package com.ryan.logisticexpress.address;

import com.ryan.logisticexpress.address.dto.ProvinceResponse;
import com.ryan.logisticexpress.address.dto.WardResponse;
import com.ryan.logisticexpress.address.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping({"/api/public/provinces", "/api/address/provinces"})
    public ResponseEntity<List<ProvinceResponse>> listProvinces() {
        return ResponseEntity.ok(addressService.listProvinces());
    }

    @GetMapping({"/api/public/provinces/{provinceCode}/wards", "/api/address/provinces/{provinceCode}/wards"})
    public ResponseEntity<List<WardResponse>> listWardsByProvince(
            @PathVariable String provinceCode
    ) {
        return ResponseEntity.ok(addressService.listWardsByProvince(provinceCode));
    }
}
