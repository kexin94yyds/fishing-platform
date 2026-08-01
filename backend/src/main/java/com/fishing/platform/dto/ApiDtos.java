package com.fishing.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 100) String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank
            @Pattern(regexp = "[a-z][a-z0-9_]{3,31}",
                    message = "须以小写字母开头，仅含小写字母、数字或下划线，长度 4-32 位")
            String username,
            @NotBlank @Size(min = 2, max = 100, message = "长度须为 2-100 位") String displayName,
            @NotBlank
            @Size(min = 8, max = 64, message = "长度须为 8-64 位")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$",
                    message = "须至少包含一个英文字母和一个数字，且不能包含空格")
            String password
    ) {
    }

    public record CsrfView(String headerName, String parameterName, String token) {
    }

    public record MeView(Long id, String username, String displayName, String role) {
    }

    public record ZoneRequest(
            Long id,
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Pattern(regexp = "ACTIVE|INACTIVE", message = "必须是 ACTIVE 或 INACTIVE") String status
    ) {
    }

    public record SpotRequest(
            Long id,
            @NotNull Long zoneId,
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 100) String name,
            @DecimalMin(value = "0.00") @jakarta.validation.constraints.DecimalMax("100.00")
            @Digits(integer = 3, fraction = 2) BigDecimal mapX,
            @DecimalMin(value = "0.00") @jakarta.validation.constraints.DecimalMax("100.00")
            @Digits(integer = 3, fraction = 2) BigDecimal mapY,
            @NotNull @Min(1) Integer capacity,
            @Pattern(regexp = "OPEN|CLOSED|MAINTENANCE",
                    message = "必须是 OPEN、CLOSED 或 MAINTENANCE") String status,
            @Size(max = 500) String note
    ) {
    }

    public record BookingRequest(
            Long memberId,
            @NotNull Long spotId,
            @NotNull @FutureOrPresent LocalDate fishingDate,
            @NotBlank @Pattern(regexp = "MORNING|AFTERNOON|EVENING",
                    message = "必须是 MORNING、AFTERNOON 或 EVENING") String timeSlot,
            @NotNull @Min(1) Integer guests,
            @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Size(max = 500) String notes
    ) {
    }

    public record CatchRequest(
            Long id,
            Long bookingId,
            @NotNull Long spotId,
            Long memberId,
            @NotNull LocalDate fishingDate,
            @NotBlank @Size(max = 100) String species,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal weight,
            @NotNull @Min(1) Integer quantity,
            @Size(max = 500) String notes,
            @Pattern(regexp = "RECORDED|VERIFIED|VOID", message = "必须是 RECORDED、VERIFIED 或 VOID") String status
    ) {
    }

    public record MemberRequest(
            Long id,
            @Size(max = 40) String memberNo,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "[0-9+\\- ]{6,32}", message = "手机号格式不正确") String phone,
            @Pattern(regexp = "NORMAL|SILVER|GOLD|VIP",
                    message = "必须是 NORMAL、SILVER、GOLD 或 VIP") String level,
            @Min(0) Integer points,
            @Pattern(regexp = "ACTIVE|INACTIVE", message = "必须是 ACTIVE 或 INACTIVE") String status
    ) {
    }

    public record ProductRequest(
            Long id,
            @NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 60) String category,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity,
            @Pattern(regexp = "ACTIVE|INACTIVE", message = "必须是 ACTIVE 或 INACTIVE") String status
    ) {
    }

    public record SalesItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity
    ) {
    }

    public record SalesOrderRequest(
            Long memberId,
            @NotEmpty @Size(max = 50) List<@Valid SalesItemRequest> items,
            @Pattern(regexp = "CASH|WECHAT|ALIPAY|CARD", message = "支付方式不正确") String paymentMethod
    ) {
    }

    public record PaymentConfirmRequest(
            @Pattern(regexp = "CASH|WECHAT|ALIPAY|CARD", message = "支付方式不正确") String method
    ) {
    }
}
