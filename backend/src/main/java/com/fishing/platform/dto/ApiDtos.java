package com.fishing.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.AssertTrue;
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

    public record RegistrationView(boolean enabled) {
    }

    public record MeView(Long id, String username, String displayName, String role) {
    }

    public record AccountCreateRequest(
            @NotBlank
            @Pattern(regexp = "[a-z][a-z0-9_]{3,31}",
                    message = "须以小写字母开头，仅含小写字母、数字或下划线，长度 4-32 位")
            String username,
            @NotBlank @Size(min = 2, max = 100) String displayName,
            @NotBlank @Size(min = 8, max = 64)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$",
                    message = "须至少包含一个英文字母和一个数字，且不能包含空格")
            String password,
            @NotBlank @Pattern(regexp = "ADMIN|OPERATOR", message = "必须是 ADMIN 或 OPERATOR") String role
    ) {
    }

    public record AccountUpdateRequest(
            @NotBlank @Size(min = 2, max = 100) String displayName,
            @NotBlank @Pattern(regexp = "ADMIN|OPERATOR", message = "必须是 ADMIN 或 OPERATOR") String role,
            @NotNull Boolean enabled,
            @NotNull @Min(0) Long expectedVersion
    ) {
    }

    public record AccountPasswordResetRequest(
            @NotBlank @Size(min = 8, max = 64)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$",
                    message = "须至少包含一个英文字母和一个数字，且不能包含空格")
            String newPassword,
            @NotNull @Min(0) Long expectedVersion
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 64) String currentPassword,
            @NotBlank @Size(min = 8, max = 64)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$",
                    message = "须至少包含一个英文字母和一个数字，且不能包含空格")
            String newPassword
    ) {
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
            @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal defaultPrice,
            @Pattern(regexp = "OPEN|CLOSED|MAINTENANCE",
                    message = "必须是 OPEN、CLOSED 或 MAINTENANCE") String status,
            @Size(max = 500) String note
    ) {
        public SpotRequest(Long id, Long zoneId, String code, String name,
                           BigDecimal mapX, BigDecimal mapY, Integer capacity,
                           String status, String note) {
            this(id, zoneId, code, name, mapX, mapY, capacity, BigDecimal.ZERO, status, note);
        }

        @AssertTrue(message = "地图坐标必须同时填写或同时留空")
        public boolean isCoordinatePairValid() {
            return (mapX == null) == (mapY == null);
        }
    }

    public record SlotInventoryRequest(
            @NotNull LocalDate fishingDate,
            @NotBlank @Pattern(regexp = "MORNING|AFTERNOON|EVENING",
                    message = "必须是 MORNING、AFTERNOON 或 EVENING") String timeSlot,
            @NotNull @Min(1) Integer capacity,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotBlank @Pattern(regexp = "AVAILABLE|CLOSED", message = "必须是 AVAILABLE 或 CLOSED") String status,
            @Min(0) Long expectedVersion
    ) {
    }

    public record BookingRequest(
            Long memberId,
            @Size(max = 100) String contactName,
            @Pattern(regexp = "[0-9+\\- ]{6,32}", message = "联系电话格式不正确") String contactPhone,
            @NotNull Long spotId,
            @NotNull LocalDate fishingDate,
            @NotBlank @Pattern(regexp = "MORNING|AFTERNOON|EVENING",
                    message = "必须是 MORNING、AFTERNOON 或 EVENING") String timeSlot,
            @NotNull @Min(1) Integer guests,
            @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Size(max = 500) String notes
    ) {
        public BookingRequest(Long memberId, Long spotId, LocalDate fishingDate, String timeSlot,
                              Integer guests, BigDecimal amount, String notes) {
            this(memberId, null, null, spotId, fishingDate, timeSlot, guests, amount, notes);
        }

        @AssertTrue(message = "散客预订必须填写顾客姓名和联系电话")
        public boolean isCustomerIdentityPresent() {
            return memberId != null
                    || (contactName != null && !contactName.isBlank()
                    && contactPhone != null && !contactPhone.isBlank());
        }
    }

    public record CatchCreateRequest(
            Long bookingId,
            @NotNull Long spotId,
            Long memberId,
            @NotNull LocalDate fishingDate,
            @Pattern(regexp = "MORNING|AFTERNOON|EVENING",
                    message = "必须是 MORNING、AFTERNOON 或 EVENING") String timeSlot,
            @NotBlank @Size(max = 100) String species,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal weight,
            @NotNull @Min(1) Integer quantity,
            @Size(max = 500) String notes
    ) {
        public CatchCreateRequest(Long bookingId, Long spotId, Long memberId,
                                  LocalDate fishingDate, String species, BigDecimal weight,
                                  Integer quantity, String notes) {
            this(bookingId, spotId, memberId, fishingDate, "MORNING", species, weight, quantity, notes);
        }
    }

    public record CatchUpdateRequest(
            Long bookingId,
            @NotNull Long spotId,
            Long memberId,
            @NotNull LocalDate fishingDate,
            @Pattern(regexp = "MORNING|AFTERNOON|EVENING",
                    message = "必须是 MORNING、AFTERNOON 或 EVENING") String timeSlot,
            @NotBlank @Size(max = 100) String species,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal weight,
            @NotNull @Min(1) Integer quantity,
            @Size(max = 500) String notes,
            @NotBlank
            @Pattern(regexp = "RECORDED|VERIFIED|VOID", message = "必须是 RECORDED、VERIFIED 或 VOID") String status,
            @NotBlank
            @Pattern(regexp = "RECORDED|VERIFIED|VOID", message = "必须是 RECORDED、VERIFIED 或 VOID") String expectedStatus,
            @NotNull @Min(0) Long expectedVersion
    ) {
        public CatchUpdateRequest(Long bookingId, Long spotId, Long memberId,
                                  LocalDate fishingDate, String species, BigDecimal weight,
                                  Integer quantity, String notes, String status,
                                  String expectedStatus, Long expectedVersion) {
            this(bookingId, spotId, memberId, fishingDate, "MORNING", species, weight,
                    quantity, notes, status, expectedStatus, expectedVersion);
        }
    }

    public record TrafficDailyUpsertRequest(
            @NotNull @Min(0) Integer visits,
            @NotNull @Min(0) Integer uniqueVisitors,
            @Size(max = 500) String notes,
            @Min(0) Long expectedVersion
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

    public record ProductUpdateRequest(
            @NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 60) String category,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity,
            @Pattern(regexp = "ACTIVE|INACTIVE", message = "必须是 ACTIVE 或 INACTIVE") String status,
            @NotNull @Min(0) Long version
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
