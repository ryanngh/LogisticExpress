package com.ryan.logisticexpress.pricing.service;

import com.ryan.logisticexpress.address.dto.ProvinceResponse;
import com.ryan.logisticexpress.address.enums.Region;
import com.ryan.logisticexpress.address.service.AddressService;
import com.ryan.logisticexpress.common.exception.ConflictException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.pricing.dto.*;
import com.ryan.logisticexpress.pricing.entity.RateCard;
import com.ryan.logisticexpress.pricing.entity.ServiceType;
import com.ryan.logisticexpress.pricing.enums.FeePayer;
import com.ryan.logisticexpress.pricing.enums.ZoneType;
import com.ryan.logisticexpress.pricing.repository.RateCardRepository;
import com.ryan.logisticexpress.pricing.repository.ServiceTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PricingEngineService {

    private static final BigDecimal INSURANCE_THRESHOLD = new BigDecimal("1000000");
    private static final BigDecimal INSURANCE_RATE = new BigDecimal("0.005");
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final ServiceTypeRepository serviceTypeRepository;
    private final RateCardRepository rateCardRepository;
    private final AddressService addressService;

    public PricingEngineService(ServiceTypeRepository serviceTypeRepository) {
        this(serviceTypeRepository, null, null);
    }

    @Autowired
    public PricingEngineService(
            ServiceTypeRepository serviceTypeRepository,
            RateCardRepository rateCardRepository,
            AddressService addressService
    ) {
        this.serviceTypeRepository = serviceTypeRepository;
        this.rateCardRepository = rateCardRepository;
        this.addressService = addressService;
    }

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

    @Transactional(readOnly = true)
    public ServiceType getServiceTypeEntityByCode(String code) {
        return serviceTypeRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("SERVICE_TYPE_NOT_FOUND", "Service type not found: " + code));
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

    @Transactional(readOnly = true)
    public List<RateCardResponse> listRateCards() {
        return rateCardRepository.findAll().stream()
                .map(RateCardResponse::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public RateCard findActiveRateCard(UUID serviceTypeId, ZoneType zoneType) {
        return rateCardRepository.findByServiceTypeIdAndZoneTypeAndIsActiveTrue(serviceTypeId, zoneType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RATE_CARD_NOT_FOUND",
                        "No active rate card for service type " + serviceTypeId + " and zone " + zoneType
                ));
    }

    @Transactional
    public RateCardResponse createRateCard(CreateRateCardRequest request) {
        ServiceType serviceType = serviceTypeRepository.findById(request.serviceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("SERVICE_TYPE_NOT_FOUND", "Service type not found: " + request.serviceTypeId()));

        rateCardRepository.findByServiceTypeIdAndZoneTypeAndIsActiveTrue(serviceType.getId(), request.zoneType())
                .ifPresent(existing -> {
                    existing.deactivate();
                    rateCardRepository.saveAndFlush(existing);
                });

        RateCard rc = RateCard.builder()
                .serviceType(serviceType)
                .zoneType(request.zoneType())
                .baseWeightGram(request.baseWeightGram())
                .basePrice(money(request.basePrice()))
                .stepWeightGram(request.stepWeightGram())
                .stepPrice(money(request.stepPrice()))
                .returnFeeRatio(request.returnFeeRatio() != null ? request.returnFeeRatio() : new BigDecimal("0.50"))
                .isActive(true)
                .effectiveFrom(Instant.now())
                .build();

        return RateCardResponse.from(rateCardRepository.save(rc));
    }

    @Transactional(readOnly = true)
    public QuoteResponse calculateQuote(QuoteRequest request) {
        ServiceType serviceType = getServiceTypeEntityByCode(request.serviceTypeCode());
        ProvinceResponse senderProvince = addressService.getProvinceResponse(request.senderProvinceCode());
        ProvinceResponse receiverProvince = addressService.getProvinceResponse(request.receiverProvinceCode());

        ZoneType zoneType = determineZoneType(
                senderProvince.region(),
                senderProvince.code(),
                receiverProvince.region(),
                receiverProvince.code()
        );

        RateCard rateCard = findActiveRateCard(serviceType.getId(), zoneType);

        int volumetric = calculateVolumetricWeight(
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                serviceType.getVolumetricDivisor()
        );
        int chargeableWeight = determineChargeableWeight(request.actualWeightGram(), volumetric);

        BigDecimal baseFee = calculateBaseFee(rateCard, chargeableWeight);
        BigDecimal insuranceFee = calculateInsuranceFee(request.declaredValue());
        BigDecimal shippingFee = calculateShippingFee(baseFee, insuranceFee);
        BigDecimal returnFee = calculateReturnFee(baseFee, rateCard.getReturnFeeRatio());

        FeePayer feePayer = request.feePayer() != null ? request.feePayer() : FeePayer.SENDER;
        BigDecimal amountToCollect = calculateAmountToCollect(request.codAmount(), shippingFee, feePayer);

        return new QuoteResponse(
                rateCard.getId(),
                serviceType.getCode(),
                serviceType.getName(),
                zoneType,
                request.actualWeightGram(),
                volumetric,
                chargeableWeight,
                baseFee,
                insuranceFee,
                shippingFee,
                returnFee,
                feePayer,
                amountToCollect
        );
    }

    public int calculateVolumetricWeight(Integer lengthCm, Integer widthCm, Integer heightCm, int divisor) {
        if (lengthCm == null || widthCm == null || heightCm == null
                || lengthCm <= 0 || widthCm <= 0 || heightCm <= 0 || divisor <= 0) {
            return 0;
        }
        long scaledVolume = (long) lengthCm * widthCm * heightCm * 1000L;
        return Math.toIntExact((scaledVolume + divisor - 1L) / divisor);
    }

    public int determineChargeableWeight(int actualWeightGram, int volumetricWeightGram) {
        return Math.max(actualWeightGram, volumetricWeightGram);
    }

    public ZoneType determineZoneType(
            Region senderRegion,
            String senderProvinceCode,
            Region receiverRegion,
            String receiverProvinceCode
    ) {
        if (senderProvinceCode.trim().equalsIgnoreCase(receiverProvinceCode.trim())) {
            return ZoneType.INTRA_PROVINCE;
        }
        if (senderRegion == receiverRegion) {
            return ZoneType.SAME_REGION;
        }
        return ZoneType.INTER_REGION;
    }

    public BigDecimal calculateBaseFee(RateCard rateCard, int chargeableWeightGram) {
        BigDecimal basePrice = money(rateCard.getBasePrice());
        int excess = Math.max(0, chargeableWeightGram - rateCard.getBaseWeightGram());
        if (excess <= 0) {
            return basePrice;
        }
        long stepWeight = rateCard.getStepWeightGram();
        long steps = ((long) excess + stepWeight - 1L) / stepWeight;
        return money(basePrice.add(rateCard.getStepPrice().multiply(BigDecimal.valueOf(steps))));
    }

    public BigDecimal calculateInsuranceFee(BigDecimal declaredValue) {
        if (declaredValue == null || declaredValue.compareTo(INSURANCE_THRESHOLD) <= 0) {
            return ZERO_MONEY;
        }
        return money(declaredValue.multiply(INSURANCE_RATE));
    }

    public BigDecimal calculateShippingFee(BigDecimal baseFee, BigDecimal insuranceFee) {
        return money(baseFee.add(insuranceFee != null ? insuranceFee : ZERO_MONEY));
    }

    public BigDecimal calculateReturnFee(BigDecimal baseFee, BigDecimal returnFeeRatio) {
        BigDecimal ratio = returnFeeRatio != null ? returnFeeRatio : new BigDecimal("0.50");
        return money(baseFee.multiply(ratio));
    }

    public BigDecimal calculateAmountToCollect(BigDecimal codAmount, BigDecimal shippingFee, FeePayer feePayer) {
        BigDecimal cod = codAmount != null ? money(codAmount) : ZERO_MONEY;
        BigDecimal ship = shippingFee != null ? money(shippingFee) : ZERO_MONEY;
        if (feePayer == FeePayer.RECEIVER) {
            return money(cod.add(ship));
        }
        return cod;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
