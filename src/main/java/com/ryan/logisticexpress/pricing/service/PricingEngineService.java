package com.ryan.logisticexpress.pricing.service;

import com.ryan.logisticexpress.address.entity.Province;
import com.ryan.logisticexpress.address.repository.ProvinceRepository;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.pricing.dto.PriceQuoteRequest;
import com.ryan.logisticexpress.pricing.dto.PriceQuoteResponse;
import com.ryan.logisticexpress.pricing.entity.RateCard;
import com.ryan.logisticexpress.pricing.entity.ServiceType;
import com.ryan.logisticexpress.pricing.enums.FeePayer;
import com.ryan.logisticexpress.pricing.enums.ZoneType;
import com.ryan.logisticexpress.pricing.exceptions.PricingNotFoundException;
import com.ryan.logisticexpress.pricing.repository.RateCardRepository;
import com.ryan.logisticexpress.pricing.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingEngineService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final RateCardRepository rateCardRepository;
    private final ProvinceRepository provinceRepository;

    private static final BigDecimal INSURANCE_THRESHOLD = new BigDecimal("1000000"); // 1.000.000 đ
    private static final BigDecimal INSURANCE_RATE = new BigDecimal("0.005");         // 0.5%

    @Transactional(readOnly = true)
    public PriceQuoteResponse calculateQuote(PriceQuoteRequest request) {
        // 1. Find the service type
        ServiceType serviceType = serviceTypeRepository.findByCodeIgnoreCase(request.serviceCode())
                .orElseThrow(() -> new PricingNotFoundException("Service not found: " + request.serviceCode()));

        // 2. Determine the pricing zone between sender and receiver provinces
        Province senderProvince = provinceRepository.findById(request.senderProvinceCode())
                .orElseThrow(() -> new ResourceNotFoundException("PROVINCE_NOT_FOUND", "Sender province not found: " + request.senderProvinceCode()));
        Province receiverProvince = provinceRepository.findById(request.receiverProvinceCode())
                .orElseThrow(() -> new ResourceNotFoundException("PROVINCE_NOT_FOUND", "Receiver province not found: " + request.receiverProvinceCode()));

        ZoneType zoneType = determineZone(senderProvince, receiverProvince);

        // 3. Calculate volumetric weight & Chargeable weight
        int volumetricGram = calculateVolumetricWeightGram(request.lengthCm(), request.widthCm(), request.heightCm(), serviceType.getVolumetricDivisor());
        int chargeableWeightGram = Math.max(request.actualWeightGram(), volumetricGram);

        // 4. Retrieve the corresponding RateCard
        RateCard rateCard = rateCardRepository.findByServiceTypeIdAndZoneType(serviceType.getId(), zoneType)
                .orElseThrow(() -> new PricingNotFoundException("Price list not configured for zone: " + zoneType));

        // 5. Calculate Base Fee: base_price + ceil(max(0, W - W_base) / step_weight) * step_price
        BigDecimal baseFee = calculateBaseFee(chargeableWeightGram, rateCard);

        // 6. Calculate Insurance Fee
        BigDecimal insuranceFee = calculateInsuranceFee(request.declaredValue());

        // Total shipping fee = Base fee + Insurance fee
        BigDecimal totalShippingFee = baseFee.add(insuranceFee);

        // Estimated return fee (based on return fee ratio)
        BigDecimal estimatedReturnFee = baseFee.multiply(rateCard.getReturnFeeRatio()).setScale(2, RoundingMode.HALF_UP);

        // 7. Amount to collect by the shipper (amountToCollect)
        BigDecimal codAmount = request.codAmount() != null ? request.codAmount() : BigDecimal.ZERO;
        FeePayer feePayer = request.feePayer() != null ? request.feePayer() : FeePayer.SENDER;

        BigDecimal amountToCollect = (feePayer == FeePayer.RECEIVER)
                ? codAmount.add(totalShippingFee)
                : codAmount;

        log.info("Successfully calculated shipping fee: Service={}, Zone={}, ChargeableWeight={}g, TotalFee={}, ToCollect={}",
                serviceType.getCode(), zoneType, chargeableWeightGram, totalShippingFee, amountToCollect);

        return new PriceQuoteResponse(
                serviceType.getCode(),
                serviceType.getName(),
                zoneType,
                request.actualWeightGram(),
                volumetricGram,
                chargeableWeightGram,
                baseFee,
                insuranceFee,
                totalShippingFee,
                estimatedReturnFee,
                amountToCollect
        );
    }

    public ZoneType determineZone(Province sender, Province receiver) {
        if (sender.getCode().equals(receiver.getCode())) {
            return ZoneType.INTRA_PROVINCE;
        }
        if (sender.getRegion() != null && sender.getRegion().equals(receiver.getRegion())) {
            return ZoneType.SAME_REGION;
        }
        return ZoneType.INTER_REGION;
    }

    public int calculateVolumetricWeightGram(Integer lengthCm, Integer widthCm, Integer heightCm, int divisor) {
        if (lengthCm == null || widthCm == null || heightCm == null || lengthCm <= 0 || widthCm <= 0 || heightCm <= 0) {
            return 0;
        }
        // Volume = (Length * Width * Height) / divisor (kg) -> Convert to grams by multiplying by 1000
        double volumetricKg = (double) (lengthCm * widthCm * heightCm) / divisor;
        return (int) Math.round(volumetricKg * 1000);
    }

    private BigDecimal calculateBaseFee(int chargeableWeightGram, RateCard rateCard) {
        BigDecimal fee = rateCard.getBasePrice();
        int excessWeight = Math.max(0, chargeableWeightGram - rateCard.getBaseWeightGram());

        if (excessWeight > 0 && rateCard.getStepWeightGram() > 0) {
            int steps = (int) Math.ceil((double) excessWeight / rateCard.getStepWeightGram());
            BigDecimal additionalFee = rateCard.getStepPrice().multiply(BigDecimal.valueOf(steps));
            fee = fee.add(additionalFee);
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateInsuranceFee(BigDecimal declaredValue) {
        if (declaredValue == null || declaredValue.compareTo(INSURANCE_THRESHOLD) <= 0) {
            return BigDecimal.ZERO;
        }
        return declaredValue.multiply(INSURANCE_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}