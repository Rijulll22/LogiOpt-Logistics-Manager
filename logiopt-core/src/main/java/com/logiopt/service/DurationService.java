package com.logiopt.service;

import com.logiopt.model.Route;
import com.logiopt.model.RouteLeg;
import com.logiopt.util.Numbers;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Calculates route duration in hours and the estimated arrival time.
 */
public class DurationService {

    private static final double MINUTES_PER_HOUR = 60.0;

    /**
     * Sum over legs of (distance / speed + buffer) + transfers x transfer hours + waiting hours.
     *
     * @param route route
     * @return duration rounded to 2 dp, or null if any leg has no speed
     */
    public Double calculateHours(Route route) {
        double hours = 0;
        for (RouteLeg leg : route.getLegs()) {
            if (leg.getSpeedKmh() == null) {
                return null;
            }
            hours += leg.getDistanceKm() / leg.getSpeedKmh() + leg.getBufferHours();
        }
        hours += route.getTransferCount() * route.getTransferHoursPerTransfer() + route.getWaitingHours();
        return Numbers.round2(hours);
    }

    /**
     * Assumption: departure is at 00:00 on the dispatch date; the duration is added in whole
     * minutes (Math.round(hours x 60)).
     *
     * @param dispatchDate dispatch date
     * @param hours        duration in hours, or null
     * @return estimated arrival, or null if the duration is unavailable
     */
    public LocalDateTime estimateArrival(LocalDate dispatchDate, Double hours) {
        if (hours == null || dispatchDate == null) {
            return null;
        }
        return dispatchDate.atStartOfDay().plusMinutes(Math.round(hours * MINUTES_PER_HOUR));
    }
}
