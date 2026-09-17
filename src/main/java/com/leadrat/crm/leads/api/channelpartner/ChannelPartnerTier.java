package com.leadrat.crm.leads.api.channelpartner;

import java.math.BigDecimal;

/**
 * Commission tier, with the default rate and the yearly booking band that earns it.
 *
 * <p>The reference computes tier assignment from booking history. There are no bookings in this
 * service, so the tier is set by hand and the band is carried purely as documentation of what the
 * tier means.
 */
public enum ChannelPartnerTier {

    SILVER(new BigDecimal("1.75"), 1, 5),
    GOLD(new BigDecimal("2.00"), 6, 15),
    PLATINUM(new BigDecimal("2.25"), 16, Integer.MAX_VALUE);

    private final BigDecimal defaultCommissionRate;
    private final int minBookingsPerYear;
    private final int maxBookingsPerYear;

    ChannelPartnerTier(BigDecimal defaultCommissionRate, int minBookingsPerYear, int maxBookingsPerYear) {
        this.defaultCommissionRate = defaultCommissionRate;
        this.minBookingsPerYear = minBookingsPerYear;
        this.maxBookingsPerYear = maxBookingsPerYear;
    }

    public BigDecimal getDefaultCommissionRate() {
        return defaultCommissionRate;
    }

    public int getMinBookingsPerYear() {
        return minBookingsPerYear;
    }

    public int getMaxBookingsPerYear() {
        return maxBookingsPerYear;
    }
}
