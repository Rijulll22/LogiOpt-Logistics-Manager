package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.ComparisonModel;
import com.logiopt.desktop.presenter.Models.ComparisonRow;
import com.logiopt.desktop.presenter.Models.OrderSummaryModel;
import com.logiopt.desktop.presenter.Models.StrategyCard;
import com.logiopt.desktop.support.Formats;
import com.logiopt.desktop.tools.SampleData;
import com.logiopt.desktop.tools.SampleData.Variant;
import com.logiopt.model.CustomerDashboard;
import com.logiopt.model.OptimisationPreference;
import com.logiopt.model.Role;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.User;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The comparison and order-summary view models built from the real planning engine and strategies
 * (SampleData runs them on in-memory copies of the seeded Mumbai -> Delhi routes, 5000 kg): best
 * values per column, "Your choice", missing metrics, excluded routes and the Book state. No database,
 * no display.
 */
class ComparisonViewModelsTest {

    private static ComparisonRow row(ComparisonModel m, long id) {
        return m.rows().stream().filter(r -> r.routeId() == id).findFirst().orElseThrow();
    }

    private static StrategyCard card(ComparisonModel m, String key) {
        return m.strategies().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void balancedRecommendsRoute2AndTheBestValuesAreMarkedPerColumn() {
        ComparisonModel m = SampleData.comparison(OptimisationPreference.BALANCED, Variant.STANDARD);
        assertTrue(m.recommendation().recommended());
        assertEquals(2, m.recommendation().routeId());
        assertEquals("Balanced score 0.167971", m.recommendation().highlight());
        assertTrue(m.recommendation().reason().contains("0.361788"), m.recommendation().reason());
        assertEquals(List.of("#2 Rail direct", "#1 Road direct", "#4 Rail electric", "#2 Rail direct"),
                m.strategies().stream().map(StrategyCard::winner).toList(), "Cheapest, Fastest, Greenest, Balanced");
        assertTrue(card(m, "BALANCED").yourChoice());
        assertFalse(card(m, "CHEAPEST").yourChoice());
        assertEquals(4, m.rows().size());
        assertTrue(row(m, 2).costBest() && !row(m, 1).costBest(), "lowest cost: route 2");
        assertTrue(row(m, 1).durationBest() && !row(m, 2).durationBest(), "shortest duration: route 1");
        assertTrue(row(m, 4).emissionsBest() && !row(m, 2).emissionsBest(), "lowest emissions: route 4");
        assertTrue(row(m, 2).reliabilityBest() && !row(m, 4).reliabilityBest(), "highest reliability: route 2");
        assertTrue(row(m, 2).yourChoice());
        assertEquals(1, m.rows().stream().filter(ComparisonRow::yourChoice).count());
        assertEquals("INR 22,800.00", row(m, 2).cost(), "thousands separators");
        assertEquals("RAIL", row(m, 2).mode());
        assertEquals("MULTIMODAL", row(m, 3).mode());
        assertTrue(m.bookable());
        assertNull(m.bookBlockedReason());
        assertTrue(m.excluded().isEmpty());
        assertEquals(3, m.charts().size());
        assertEquals(4, m.charts().get(0).bars().size());
        assertEquals(List.of(1L, 2L, 3L, 4L), List.copyOf(m.legend().keySet()));
    }

    @Test
    void anotherPreferenceMovesYourChoiceButHidesNoStrategy() {
        ComparisonModel m = SampleData.comparison(OptimisationPreference.GREENEST, Variant.STANDARD);
        assertEquals("GREENEST", m.preferredKey());
        assertEquals(4, m.recommendation().routeId());
        assertEquals("Emissions 145.00 kg CO2e", m.recommendation().highlight());
        assertTrue(row(m, 4).yourChoice());
        assertFalse(row(m, 2).yourChoice());
        assertEquals(4, m.strategies().size(), "all four strategies stay");
        assertTrue(card(m, "GREENEST").yourChoice());
    }

    @Test
    void aMissingMetricReadsNotAvailableAndIsNeverTheBest() {
        ComparisonModel m = SampleData.comparison(OptimisationPreference.BALANCED, Variant.MISSING_METRIC);
        assertEquals(Formats.NOT_AVAILABLE, row(m, 2).emissions());
        assertFalse(row(m, 2).emissionsBest());
        assertTrue(row(m, 4).emissionsBest());
        assertEquals(3, m.charts().get(2).bars().size(), "the route without emissions has no bar");
        assertTrue(m.charts().get(2).missingNote().contains("#2 Rail direct"));
        assertTrue(m.excluded().stream().anyMatch(x -> x.route().equals("#2 Rail direct")
                && x.reason().startsWith("Not considered by Balanced")), m.excluded().toString());
    }

    @Test
    void routesThatDoNotFitAreListedSeparatelyWithTheirReason() {
        ComparisonModel m = SampleData.comparison(OptimisationPreference.CHEAPEST, Variant.EXCLUDED_ROUTE);
        assertEquals(4, m.rows().size());
        assertTrue(m.rows().stream().noneMatch(r -> r.routeId() == 5));
        assertEquals(1, m.excluded().size());
        assertEquals("#5 Road small truck", m.excluded().get(0).route());
        assertTrue(m.excluded().get(0).reason().startsWith("Not eligible: "), m.excluded().get(0).reason());
    }

    @Test
    void pastDatedShipmentsCanBeComparedButNotBooked() {
        ComparisonModel m = SampleData.comparison(OptimisationPreference.FASTEST, Variant.PAST_DATED);
        assertEquals(4, m.rows().size());
        assertFalse(m.bookable());
        assertTrue(m.bookBlockedReason().contains("is in the past"), m.bookBlockedReason());
        assertEquals(1, m.recommendation().routeId(), "Fastest still recommends");
    }

    @Test
    void whenThePreferredStrategyCannotRankAnyRouteTheReasonIsShownAndBookingStaysPossible() {
        ComparisonModel m = SampleData.comparison(OptimisationPreference.GREENEST, Variant.NO_GREENEST);
        assertFalse(m.recommendation().recommended());
        assertTrue(m.recommendation().reason().contains("You can still book any eligible route"),
                m.recommendation().reason());
        assertNull(card(m, "GREENEST").winner());
        assertTrue(m.bookable());
        assertTrue(m.rows().stream().noneMatch(ComparisonRow::yourChoice));
        assertTrue(m.rows().stream().allMatch(r -> r.emissions().equals(Formats.NOT_AVAILABLE) && !r.emissionsBest()));
    }

    @Test
    void orderSummaryShowsEveryLegAndTheCostBreakdownFromTheEvaluation() {
        OrderSummaryModel o = SampleData.orderSummary();
        assertEquals(2, o.routeId());
        assertEquals("BALANCED", o.selected(), "pre-selected from the preference");
        assertEquals(4, o.strategies().size());
        assertEquals(1, o.summary().legs().size());
        assertEquals("1,450 km", o.summary().legs().get(0).distance());
        assertEquals(List.of("Transport", "Handling", "Transfers", "Waiting"),
                o.summary().costs().stream().map(Models.CostLine::label).toList());
        assertEquals("INR 20,300.00", o.summary().costs().get(0).amount());
        assertEquals("INR 22,800.00", o.summary().total());
        assertEquals("38.22 h", o.summary().duration());
        assertEquals("217.50 kg CO2e", o.summary().emissions());
        assertEquals("2026-11-03", o.summary().dispatch());
        assertEquals("Balanced", o.summary().preference());
    }

    @Test
    void theDashboardDoesNotShowTheUnusedPlannedStatus() {
        Map<ShipmentStatus, Integer> counts = new EnumMap<>(ShipmentStatus.class);
        counts.put(ShipmentStatus.PLANNED, 2);
        counts.put(ShipmentStatus.DRAFT, 1);
        Models.DashboardModel d = ViewModels.customerDashboard(new CustomerDashboard(counts, Map.of(), 0, List.of(), 20,
                false), new User(3, "a@example.test", "Asha", Role.CUSTOMER, true));
        assertEquals("1 draft", d.cards().get(0).caption());
    }

    @Test
    void preferenceOptionsAreTheFourStrategiesWithDescriptions() {
        assertEquals(List.of("CHEAPEST", "FASTEST", "GREENEST", "BALANCED"),
                ViewModels.preferenceOptions().stream().map(Models.PreferenceOption::key).toList());
        assertEquals("Best overall mix", ViewModels.preferenceOptions().get(3).description());
    }
}
