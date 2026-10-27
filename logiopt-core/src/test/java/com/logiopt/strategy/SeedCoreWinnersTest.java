package com.logiopt.strategy;

import com.logiopt.TestData;
import com.logiopt.model.Recommendation;
import com.logiopt.model.Route;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SEED-CORE-4 (routes 1-4 of seed.sql) using in-memory fixtures; needs no database.
 */
class SeedCoreWinnersTest {

    private static final double SCORE_EPS = 1e-5;

    /** @return routes 1-4, identical to seed.sql */
    static List<Route> seedCore4() {
        return List.of(TestData.route1(), TestData.route2(), TestData.route3(), TestData.route4());
    }

    @Test
    void route4Values() {
        RouteEvaluation r4 = TestData.evaluate(seedCore4()).get(3);
        assertEquals(new BigDecimal("22475.00"), r4.getCostBreakdown().getTransport());
        assertEquals(new BigDecimal("2800.00"), r4.getCostBreakdown().getHandling());
        assertEquals(new BigDecimal("25275.00"), r4.getTotalCost());
        assertEquals(44.25, r4.getDurationHours(), TestData.EPS);
        assertEquals(145.00, r4.getEmissionsKg(), TestData.EPS);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"CHEAPEST, 2", "FASTEST, 1", "GREENEST, 4", "BALANCED, 2"})
    void winners(String key, long winner) throws Exception {
        Recommendation rec = new RecommendationService().recommend(key, TestData.evaluate(seedCore4()));
        assertEquals(winner, rec.getChosen().getRouteId());
    }

    @Test
    void balancedScoresAndThreeDistinctWinners() throws Exception {
        RecommendationService service = new RecommendationService();
        List<RouteEvaluation> evaluations = TestData.evaluate(seedCore4());
        Recommendation balanced = service.recommend("BALANCED", evaluations);
        assertEquals(0.625000, balanced.getScores().get(1L).getTotalScore(), SCORE_EPS);
        assertEquals(0.167971, balanced.getScores().get(2L).getTotalScore(), SCORE_EPS);
        assertEquals(0.609594, balanced.getScores().get(3L).getTotalScore(), SCORE_EPS);
        assertEquals(0.361788, balanced.getScores().get(4L).getTotalScore(), SCORE_EPS);
        assertEquals(List.of(2L, 4L, 3L, 1L), TestData.ids(balanced.getRanking()));
        Set<Long> winners = new HashSet<>();
        for (OptimizationStrategy s : service.getStrategies()) {
            winners.add(s.recommend(evaluations).getChosen().getRouteId());
        }
        assertEquals(Set.of(1L, 2L, 4L), winners);
    }
}
