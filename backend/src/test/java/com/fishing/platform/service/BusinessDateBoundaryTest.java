package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.CatchRecord;
import com.fishing.platform.domain.DomainModels.Spot;
import com.fishing.platform.domain.DomainModels.TrafficPoint;
import com.fishing.platform.domain.DomainModels.Zone;
import com.fishing.platform.dto.ApiDtos.BookingRequest;
import com.fishing.platform.dto.ApiDtos.CatchCreateRequest;
import com.fishing.platform.dto.ApiDtos.CatchUpdateRequest;
import com.fishing.platform.dto.ApiDtos.SpotRequest;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.CatchMapper;
import com.fishing.platform.mapper.DashboardMapper;
import com.fishing.platform.mapper.MemberMapper;
import com.fishing.platform.mapper.SpotMapper;
import com.fishing.platform.mapper.ZoneMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BusinessDateBoundaryTest {
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final LocalDate SHANGHAI_DATE = LocalDate.of(2026, 8, 8);
    private static final Clock SHANGHAI_BOUNDARY_CLOCK = Clock.fixed(
            Instant.parse("2026-08-07T16:30:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void catchCreationTreatsShanghaiDateAsTodayWhileUtcIsStillPreviousDay() {
        CatchMapper catchMapper = mock(CatchMapper.class);
        BookingMapper bookingMapper = mock(BookingMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        SpotMapper spotMapper = mock(SpotMapper.class);
        ZoneMapper zoneMapper = mock(ZoneMapper.class);
        Booking booking = new Booking(
                7L, "BK-TEST", 3L, "测试会员", 4L, "测试钓位", "测试区",
                SHANGHAI_DATE, "MORNING", 1, BigDecimal.ZERO, "CONFIRMED",
                null, null, null, null);
        when(bookingMapper.lockById(7L)).thenReturn(7L);
        when(bookingMapper.findById(7L)).thenReturn(booking);

        CatchService service = new CatchService(
                catchMapper, bookingMapper, memberMapper, spotMapper, zoneMapper, SHANGHAI_BOUNDARY_CLOCK);
        service.create(new CatchCreateRequest(
                7L, 99L, 99L, SHANGHAI_DATE, "鲫鱼",
                BigDecimal.ONE, 1, null));

        verify(catchMapper).insert(
                anyString(), eq(7L), eq(4L), eq(3L), eq(SHANGHAI_DATE), eq("鲫鱼"),
                eq(BigDecimal.ONE), eq(1), eq(null), eq("RECORDED"));
    }

    @Test
    void bookingCreationRejectsShanghaiYesterdayWhileUtcStillCallsItToday() {
        BookingMapper bookingMapper = mock(BookingMapper.class);
        SpotMapper spotMapper = mock(SpotMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        ZoneMapper zoneMapper = mock(ZoneMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        BookingService service = new BookingService(
                bookingMapper, spotMapper, memberMapper, zoneMapper,
                currentUserService, SHANGHAI_BOUNDARY_CLOCK);

        BookingRequest request = new BookingRequest(
                null, 4L, SHANGHAI_DATE.minusDays(1), "MORNING", 1,
                BigDecimal.ZERO, null);

        assertThrows(BusinessException.class, () -> service.create(request));
        verifyNoInteractions(bookingMapper, spotMapper, memberMapper, zoneMapper, currentUserService);
    }

    @Test
    void spotCapacityCheckStartsFromShanghaiToday() {
        SpotMapper mapper = mock(SpotMapper.class);
        ZoneMapper zoneMapper = mock(ZoneMapper.class);
        Spot existing = new Spot(
                9L, 3L, "测试分区", "T-1", "测试钓位", BigDecimal.TEN, BigDecimal.TEN,
                1, "OPEN", null, null, null);
        Zone zone = new Zone(3L, "TEST", "测试分区", null, "ACTIVE", null, null);
        when(mapper.lockById(9L)).thenReturn(9L);
        when(mapper.findById(9L)).thenReturn(existing);
        when(zoneMapper.findByIdForUpdate(3L)).thenReturn(zone);
        when(mapper.updateIfCapacityAllows(
                eq(9L), eq(3L), eq("T-1"), eq("测试钓位"), eq(BigDecimal.TEN),
                eq(BigDecimal.TEN), eq(1), eq("OPEN"), eq(null), eq(SHANGHAI_DATE)))
                .thenReturn(1);

        SpotService service = new SpotService(mapper, zoneMapper, SHANGHAI_BOUNDARY_CLOCK);
        service.update(9L, new SpotRequest(
                null, 3L, "T-1", "测试钓位", BigDecimal.TEN, BigDecimal.TEN,
                1, "OPEN", null));

        verify(mapper).updateIfCapacityAllows(
                eq(9L), eq(3L), eq("T-1"), eq("测试钓位"), eq(BigDecimal.TEN),
                eq(BigDecimal.TEN), eq(1), eq("OPEN"), eq(null), eq(SHANGHAI_DATE));
        verify(mapper).updateFutureInventoryCapacity(9L, 1, SHANGHAI_DATE);
    }

    @Test
    void dashboardQueriesUseShanghaiTodayInsteadOfDatabaseCurrentDate() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        when(analyticsService.trafficSeries(7)).thenReturn(List.of(
                new TrafficPoint(SHANGHAI_DATE, 1, 1, 0, 0, BigDecimal.ZERO)));

        DashboardService service = new DashboardService(
                mapper, analyticsService, SHANGHAI_BOUNDARY_CLOCK);
        service.summary();

        verify(mapper).todayBookings(SHANGHAI_DATE);
        verify(mapper).todayRevenue(
                SHANGHAI_DATE.atStartOfDay(), SHANGHAI_DATE.plusDays(1).atStartOfDay());
        verify(mapper).todayCatchCount(SHANGHAI_DATE);
        verify(mapper).bookingMix(SHANGHAI_DATE);
    }

    @Test
    void legacyFutureCatchCanBeVoidedWithoutChangingHistoricalAssociation() {
        CatchMapper catchMapper = mock(CatchMapper.class);
        BookingMapper bookingMapper = mock(BookingMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        SpotMapper spotMapper = mock(SpotMapper.class);
        ZoneMapper zoneMapper = mock(ZoneMapper.class);
        LocalDate futureDate = SHANGHAI_DATE.plusDays(1);
        CatchRecord existing = new CatchRecord(
                5L, "CR-LEGACY", 7L, "BK-LEGACY", 4L, "测试钓位", 3L, "测试会员",
                futureDate, "鲤鱼", TWO, 1, "旧记录", "RECORDED",
                0L, null, null);
        when(catchMapper.lockById(5L)).thenReturn(5L);
        when(catchMapper.findById(5L)).thenReturn(existing);
        when(catchMapper.update(
                eq(5L), eq(7L), eq(4L), eq(3L), eq(futureDate), eq("鲤鱼"),
                eq(TWO), eq(1), eq("确认作废"), eq("VOID"), eq("RECORDED"), eq(0L)))
                .thenReturn(1);

        CatchService service = new CatchService(
                catchMapper, bookingMapper, memberMapper, spotMapper, zoneMapper, SHANGHAI_BOUNDARY_CLOCK);
        service.update(5L, new CatchUpdateRequest(
                7L, 4L, 3L, futureDate, "鲤鱼",
                TWO, 1, "确认作废", "VOID", "RECORDED", 0L));

        verifyNoInteractions(bookingMapper);
        verify(catchMapper).update(
                eq(5L), eq(7L), eq(4L), eq(3L), eq(futureDate), eq("鲤鱼"),
                eq(TWO), eq(1), eq("确认作废"), eq("VOID"), eq("RECORDED"), eq(0L));
    }

    @Test
    void voidingCatchRejectsAssociationChangesInsteadOfSilentlyDiscardingThem() {
        CatchMapper catchMapper = mock(CatchMapper.class);
        BookingMapper bookingMapper = mock(BookingMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        SpotMapper spotMapper = mock(SpotMapper.class);
        ZoneMapper zoneMapper = mock(ZoneMapper.class);
        CatchRecord existing = new CatchRecord(
                5L, "CR-LEGACY", 7L, "BK-LEGACY", 4L, "测试钓位", 3L, "测试会员",
                SHANGHAI_DATE, "鲤鱼", TWO, 1, "旧记录", "RECORDED", 0L, null, null);
        when(catchMapper.lockById(5L)).thenReturn(5L);
        when(catchMapper.findById(5L)).thenReturn(existing);

        CatchService service = new CatchService(
                catchMapper, bookingMapper, memberMapper, spotMapper, zoneMapper, SHANGHAI_BOUNDARY_CLOCK);
        assertThrows(BusinessException.class, () -> service.update(5L, new CatchUpdateRequest(
                7L, 99L, 3L, SHANGHAI_DATE, "鲤鱼",
                TWO, 1, "确认作废", "VOID", "RECORDED", 0L)));
        verifyNoInteractions(bookingMapper);
    }
}
