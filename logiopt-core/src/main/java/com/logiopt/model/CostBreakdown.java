package com.logiopt.model;

import com.logiopt.util.Numbers;

import java.math.BigDecimal;

/**
 * Immutable cost breakdown in INR. Each component is rounded to 2 decimals (HALF_UP);
 * the total is the sum of the rounded components and is not stored.
 */
public final class CostBreakdown {

    private final BigDecimal transport;
    private final BigDecimal handling;
    private final BigDecimal transfer;
    private final BigDecimal waiting;

    /**
     * Creates a breakdown, rounding each component to 2 decimals.
     *
     * @param transport transport cost (not null)
     * @param handling  handling cost (not null)
     * @param transfer  transfer cost (not null)
     * @param waiting   waiting cost (not null)
     */
    public CostBreakdown(BigDecimal transport, BigDecimal handling, BigDecimal transfer, BigDecimal waiting) {
        this.transport = Numbers.money(transport);
        this.handling = Numbers.money(handling);
        this.transfer = Numbers.money(transfer);
        this.waiting = Numbers.money(waiting);
    }

    /** @return transport cost */
    public BigDecimal getTransport() {
        return transport;
    }

    /** @return handling cost */
    public BigDecimal getHandling() {
        return handling;
    }

    /** @return transfer cost */
    public BigDecimal getTransfer() {
        return transfer;
    }

    /** @return waiting cost */
    public BigDecimal getWaiting() {
        return waiting;
    }

    /**
     * Returns the total as the sum of the four rounded components.
     *
     * @return total cost with scale 2
     */
    public BigDecimal getTotal() {
        return transport.add(handling).add(transfer).add(waiting);
    }
}
