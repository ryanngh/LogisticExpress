package com.ryan.logisticexpress.ops;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.pricing.enums.FeePayer;
import com.ryan.logisticexpress.security.CustomUserDetails;
import com.ryan.logisticexpress.shipment.dto.ShipmentDetailResponse;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import com.ryan.logisticexpress.shipment.dto.TrackingEventResponse;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import com.ryan.logisticexpress.shipment.exceptions.ShipmentNotFoundException;
import com.ryan.logisticexpress.shipment.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/ops")
@RequiredArgsConstructor
public class OpsPageController {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(VN_ZONE);
    private static final Set<ShipmentStatus> FAILURE_OR_RETURN_STATUSES = EnumSet.of(
            ShipmentStatus.DELIVERY_FAILED,
            ShipmentStatus.REFUSED,
            ShipmentStatus.ON_HOLD_FOR_RETURN,
            ShipmentStatus.RETURN_INITIATED,
            ShipmentStatus.IN_RETURN_TRANSIT,
            ShipmentStatus.RETURNED,
            ShipmentStatus.CANCELLED
    );

    private final ShipmentService shipmentService;

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Authentication auth, Model model) {
        return renderOpsPage("dashboard", "ops/dashboard", auth, model);
    }

    @GetMapping("/shipments")
    public String shipments(Authentication auth, Model model) {
        return renderOpsPage("shipments", "ops/shipments", auth, model);
    }

    @GetMapping("/shipments/{trackingCode}")
    public String shipmentDetail(@PathVariable String trackingCode, Authentication auth, Model model) {
        String cleanCode = trackingCode != null ? trackingCode.trim().toUpperCase() : "";
        model.addAttribute("trackingCode", cleanCode);

        UUID currentUserId = null;
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            currentUserId = user.getId();
        }

        try {
            ShipmentDetailResponse detail = shipmentService.getShipmentDetail(cleanCode, currentUserId, true);
            ShipmentResponse shipment = detail.shipment();

            List<TrackingEventResponse> eventsDesc = new ArrayList<>(detail.events());
            Collections.reverse(eventsDesc);

            Map<UUID, String> eventTimes = new LinkedHashMap<>();
            for (TrackingEventResponse ev : eventsDesc) {
                eventTimes.put(ev.id(), formatVnTime(ev.occurredAt()));
            }

            boolean canReweigh = shipment.currentStatus() == ShipmentStatus.AT_ORIGIN_HUB
                    || (shipment.currentStatus() == ShipmentStatus.AT_DEST_HUB
                    && shipment.originHubId() != null
                    && shipment.originHubId().equals(shipment.currentHubId()));

            BigDecimal cod = safeAmount(shipment.codAmount());
            BigDecimal shippingFee = safeAmount(shipment.shippingFee());
            BigDecimal netPayout = shipment.feePayer() == FeePayer.RECEIVER
                    ? cod
                    : cod.subtract(shippingFee);

            model.addAttribute("notFound", false);
            model.addAttribute("shipment", shipment);
            model.addAttribute("events", eventsDesc);
            model.addAttribute("eventTimes", eventTimes);
            model.addAttribute("barcodeBase64", detail.barcodeBase64());
            model.addAttribute("qrCodeBase64", detail.qrCodeBase64());
            model.addAttribute("canReweigh", canReweigh);
            model.addAttribute("isFailureOrReturn", FAILURE_OR_RETURN_STATUSES.contains(shipment.currentStatus()));

            model.addAttribute("createdAtFormatted", formatVnTime(shipment.createdAt()));
            model.addAttribute("deliveredAtFormatted", formatVnTime(shipment.deliveredAt()));
            model.addAttribute("pickupAddressText", formatAddress(shipment.pickupAddress()));
            model.addAttribute("deliveryAddressText", formatAddress(shipment.deliveryAddress()));

            model.addAttribute("declaredValueFormatted", formatVnd(shipment.declaredValue()));
            model.addAttribute("codFormatted", formatVnd(cod));
            model.addAttribute("baseFeeFormatted", formatVnd(shipment.baseFee()));
            model.addAttribute("shippingFeeFormatted", formatVnd(shippingFee));
            model.addAttribute("insuranceFeeFormatted", formatVnd(shipment.insuranceFee()));
            model.addAttribute("returnFeeFormatted", formatVnd(shipment.returnFee()));
            model.addAttribute("amountToCollectFormatted", formatVnd(shipment.amountToCollect()));
            model.addAttribute("netPayoutFormatted", formatVnd(netPayout));
        } catch (ShipmentNotFoundException ex) {
            model.addAttribute("notFound", true);
            model.addAttribute("shipment", null);
            model.addAttribute("events", List.of());
            model.addAttribute("eventTimes", Map.of());
            model.addAttribute("canReweigh", false);
            model.addAttribute("isFailureOrReturn", false);
        }

        return renderOpsPage("shipments", "ops/shipment-detail", auth, model);
    }

    @GetMapping("/hubs")
    public String hubs(Authentication auth, Model model) {
        return renderOpsPage("hubs", "ops/hubs", auth, model);
    }

    @GetMapping("/users")
    public String users(Authentication auth, Model model) {
        return renderOpsPage("users", "ops/users", auth, model);
    }

    @GetMapping("/fleet")
    public String fleet(Authentication auth, Model model) {
        return renderPlaceholder("fleet", "Đội xe & Hoạt động giao nhận",
                "Theo dõi năng lực đội ngũ Shipper, biển số xe, tải trọng và tối ưu tuyến VRPTW.", auth, model);
    }

    @GetMapping("/addresses")
    public String addresses(Authentication auth, Model model) {
        return renderPlaceholder("addresses", "Danh mục địa chỉ & Khu vực phục vụ",
                "Tra cứu địa giới hành chính 2 cấp (Tỉnh/Thành - Phường/Xã) và vùng phục vụ Hub.", auth, model);
    }

    @GetMapping("/finance")
    public String finance(Authentication auth, Model model) {
        return renderOpsPage("finance", "ops/finance", auth, model);
    }

    @GetMapping("/reports")
    public String reports(Authentication auth, Model model) {
        return renderPlaceholder("reports", "Báo cáo & Phân tích chuyên sâu",
                "Thống kê sản lượng, tỷ lệ giao thành công lần đầu, SLA và hiệu suất từng Hub.", auth, model);
    }

    @GetMapping("/settings")
    public String settings(Authentication auth, Model model) {
        return renderPlaceholder("settings", "Cấu hình hệ thống & Nhật ký Audit",
                "Quản lý cấu hình vận hành và nhật ký truy cập dữ liệu cá nhân (Luật 91/2025/QH15).", auth, model);
    }

    private String renderPlaceholder(
            String activeNav,
            String sectionTitle,
            String sectionDescription,
            Authentication auth,
            Model model
    ) {
        model.addAttribute("sectionTitle", sectionTitle);
        model.addAttribute("sectionDescription", sectionDescription);
        return renderOpsPage(activeNav, "ops/placeholder", auth, model);
    }

    private String renderOpsPage(String activeNav, String viewName, Authentication auth, Model model) {
        model.addAttribute("activeNav", activeNav);
        model.addAttribute("isAuthenticated", true);
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            model.addAttribute("currentUsername", user.getUsername());
            model.addAttribute("currentEmail", user.getEmail());
            List<String> roles = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .toList();
            model.addAttribute("currentRoles", roles);
        }
        return viewName;
    }

    private String formatVnTime(Instant instant) {
        return instant != null ? VN_DATE_TIME.format(instant) : "—";
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String formatVnd(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        symbols.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(safeAmount(amount)) + " đ";
    }

    private String formatAddress(AddressResponse addr) {
        if (addr == null) {
            return "—";
        }
        List<String> parts = new ArrayList<>();
        if (addr.streetDetail() != null && !addr.streetDetail().isBlank()) parts.add(addr.streetDetail().trim());
        if (addr.wardName() != null && !addr.wardName().isBlank()) parts.add(addr.wardName().trim());
        if (addr.provinceName() != null && !addr.provinceName().isBlank()) parts.add(addr.provinceName().trim());
        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }
        return addr.rawText() != null && !addr.rawText().isBlank() ? addr.rawText() : "—";
    }
}
