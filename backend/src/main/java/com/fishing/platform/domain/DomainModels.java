package com.fishing.platform.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DomainModels {
    private DomainModels() {
    }

    public record UserAccount(
            Long id,
            String username,
            String passwordHash,
            String displayName,
            String role,
            boolean enabled,
            Long version,
            Long sessionVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record AccountView(
            Long id,
            String username,
            String displayName,
            String role,
            boolean enabled,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static AccountView from(UserAccount account) {
            return new AccountView(account.id(), account.username(), account.displayName(), account.role(),
                    account.enabled(), account.version(), account.createdAt(), account.updatedAt());
        }
    }

    public record AccountAudit(
            Long id,
            Long actorUserId,
            String actorUsername,
            Long targetUserId,
            String targetUsername,
            String action,
            String beforeRole,
            String afterRole,
            Boolean beforeEnabled,
            Boolean afterEnabled,
            LocalDateTime createdAt
    ) {
    }

    public record Zone(
            Long id,
            String code,
            String name,
            String description,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record Spot(
            Long id,
            Long zoneId,
            String zoneName,
            String code,
            String name,
            BigDecimal mapX,
            BigDecimal mapY,
            Integer capacity,
            String status,
            String note,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record SpotAvailability(
            Long spotId,
            String spotCode,
            String spotName,
            Long zoneId,
            String zoneName,
            LocalDate fishingDate,
            String timeSlot,
            Integer capacity,
            Integer reservedCount,
            Integer availableCount,
            String status
    ) {
    }

    public record Booking(
            Long id,
            String bookingNo,
            Long memberId,
            String memberName,
            Long spotId,
            String spotName,
            String zoneName,
            LocalDate fishingDate,
            String timeSlot,
            Integer guests,
            BigDecimal amount,
            String status,
            String notes,
            LocalDateTime cancelledAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CatchRecord(
            Long id,
            String catchNo,
            Long bookingId,
            String bookingNo,
            Long spotId,
            String spotName,
            Long memberId,
            String memberName,
            LocalDate fishingDate,
            String species,
            BigDecimal weight,
            Integer quantity,
            String notes,
            String status,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record Member(
            Long id,
            String memberNo,
            String name,
            String phone,
            String level,
            Integer points,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record Product(
            Long id,
            String sku,
            String name,
            String category,
            BigDecimal price,
            Integer stockQuantity,
            String status,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record SalesOrder(
            Long id,
            String orderNo,
            Long memberId,
            String memberName,
            BigDecimal totalAmount,
            String status,
            String paymentStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record SalesOrderItem(
            Long id,
            Long orderId,
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineAmount
    ) {
    }

    public record SalesOrderDetail(
            SalesOrder order,
            java.util.List<SalesOrderItem> items
    ) {
    }

    public record Payment(
            Long id,
            String paymentNo,
            String businessType,
            Long businessId,
            String businessNo,
            BigDecimal amount,
            String method,
            String status,
            LocalDateTime confirmedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record TrafficPoint(
            LocalDate statDate,
            Integer visits,
            Integer uniqueVisitors,
            Integer newMembers,
            Integer bookingCount,
            BigDecimal revenue
    ) {
    }

    public record TrafficDailyEntry(
            Long id,
            LocalDate statDate,
            Integer visits,
            Integer uniqueVisitors,
            String notes,
            Long version,
            Long createdBy,
            String createdByName,
            Long updatedBy,
            String updatedByName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record DailyCount(LocalDate statDate, Long metricValue) {
    }

    public record DailyAmount(LocalDate statDate, BigDecimal metricValue) {
    }

    public record DashboardMetric(String name, Long metricValue) {
    }

    public record RecentBooking(
            String bookingNo,
            String memberName,
            String spotName,
            LocalDate fishingDate,
            String timeSlot,
            String status,
            BigDecimal amount
    ) {
    }
}
