package com.logiopt.strategy;

import com.logiopt.TestData;
import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.model.Recommendation;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.ScoreBreakdown;
import com.logiopt.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyTest {

    private static final double NORM_EPS = 1e-6;
    private static final double SCORE_EPS = 1e-5;
    private static final List<String> KEYS = List.of("CHEAPEST", "FASTEST", "GREENEST", "BALANCED");

    private final RecommendationService service = new RecommendationService();

    private Recommendation run(String key, List<RouteEvaluation> evaluations) throws InvalidStrategyException {
        return service.requireStrategy(key).recommend(evaluations);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"CHEAPEST, 2, '2;3;1'", "FASTEST, 1, '1;2;3'", "GREENEST, 2, '2;3;1'", "BALANCED, 2, '2;3;1'"})
    void dataset3Winners(String key, long winner, String ranking) throws Exception {
        List<RouteEvaluation> evaluations = TestData.evaluate(TestData.dataset3());
        Recommendation rec = run(key, evaluations);
        assertEquals(winner, rec.getChosen().getRouteId());
        assertEquals(TestData.parseIds(ranking), TestData.ids(rec.getRanking()));
        assertSame(rec.getChosen(), rec.getRanking().get(0));
        for (RouteEvaluation ranked : rec.getRanking()) {
            assertSame(evaluations.get((int) ranked.getRouteId() - 1), ranked);
        }
        assertTrue(rec.getReason().contains("(route " + winner + ")"), rec.getReason());
    }

    @Test
    void cheapestReasonNamesWinnerValueAndRunnerUp() throws Exception {
        Recommendation rec = run("CHEAPEST", TestData.evaluate(TestData.dataset3()));
        assertTrue(rec.getReason().contains("Rail direct (route 2)"));
        assertTrue(rec.getReason().contains("INR 22800.00"));
        assertTrue(rec.getReason().contains("runner-up Road-Rail-Road (route 3) at INR 26075.00"), rec.getReason());
    }

    @ParameterizedTest(name = "route {0}")
    @CsvSource({
        "1, 1, 0, 1, 0.5, 0.625000",
        "2, 0, 0.460246, 0, 0, 0.138074",
        "3, 0.321078, 1, 0.101818, 1, 0.582741"
    })
    void balancedNormalizedTermsAndScores(long id, double nCost, double nTime, double nEmissions,
                                          double nReliability, double score) throws Exception {
        Recommendation rec = run("BALANCED", TestData.evaluate(TestData.dataset3()));
        ScoreBreakdown s = rec.getScores().get(id);
        assertEquals(nCost, s.getNormalizedCost(), NORM_EPS);
        assertEquals(nTime, s.getNormalizedTime(), NORM_EPS);
        assertEquals(nEmissions, s.getNormalizedEmissions(), NORM_EPS);
        assertEquals(nReliability, s.getNormalizedReliability(), NORM_EPS);
        assertEquals(score, s.getTotalScore(), SCORE_EPS);
        assertEquals(BalancedStrategy.W_COST * nCost, s.getWeightedCost(), NORM_EPS);
        assertEquals(BalancedStrategy.W_RELIABILITY * nReliability, s.getWeightedReliability(), NORM_EPS);
    }

    @Test
    void balancedReasonShowsContributions() throws Exception {
        Recommendation rec = run("BALANCED", TestData.evaluate(TestData.dataset3()));
        assertSame(rec.getScores().get(2L), rec.getChosenScore());
        assertTrue(rec.getReason().contains("score 0.138074"), rec.getReason());
        assertTrue(rec.getReason().contains("time 0.138074"), rec.getReason());
        assertTrue(rec.getReason().contains("relative to the routes currently compared"));
    }

    @Test
    void tieBreakByRouteIdIndependentOfOrder() throws Exception {
        List<RouteEvaluation> forward = TestData.evaluate(List.of(TestData.rail(7, "2.80", 0.03, 0.9),
                TestData.rail(5, "2.80", 0.03, 0.9)));
        List<RouteEvaluation> reversed = new ArrayList<>(forward);
        java.util.Collections.reverse(reversed);
        for (String key : KEYS) {
            assertEquals(List.of(5L, 7L), TestData.ids(run(key, forward).getRanking()), key);
            assertEquals(List.of(5L, 7L), TestData.ids(run(key, reversed).getRanking()), key);
        }
    }

    @Test
    void missingEmissionsExcludedOnlyFromGreenestAndBalanced() throws Exception {
        List<RouteEvaluation> evaluations = TestData.evaluate(List.of(TestData.route1(), TestData.route2(),
                TestData.rail(5, "2.00", null, 0.9)));
        assertEquals(3, evaluations.size());
        assertFalse(evaluations.get(2).isEmissionsAvailable());
        Recommendation cheapest = run("CHEAPEST", evaluations);
        assertEquals(5L, cheapest.getChosen().getRouteId());
        assertTrue(TestData.ids(run("FASTEST", evaluations).getRanking()).contains(5L));
        Recommendation greenest = run("GREENEST", evaluations);
        assertFalse(TestData.ids(greenest.getRanking()).contains(5L));
        assertEquals("Emissions not available", greenest.getExclusionReasons().get(5L));
        Recommendation balanced = run("BALANCED", evaluations);
        assertFalse(TestData.ids(balanced.getRanking()).contains(5L));
        assertTrue(balanced.getExclusionReasons().get(5L).contains("emissions"));
        assertTrue(cheapest.getExcluded().isEmpty());
    }

    @Test
    void missingReliabilityExcludedOnlyFromBalanced() throws Exception {
        List<RouteEvaluation> evaluations = TestData.evaluate(List.of(TestData.route1(), TestData.route2(),
                TestData.rail(6, "2.00", 0.01, null)));
        for (String key : List.of("CHEAPEST", "FASTEST", "GREENEST")) {
            assertTrue(TestData.ids(run(key, evaluations).getRanking()).contains(6L), key);
        }
        Recommendation balanced = run("BALANCED", evaluations);
        assertFalse(TestData.ids(balanced.getRanking()).contains(6L));
        assertTrue(balanced.getExclusionReasons().get(6L).contains("reliability"));
    }

    @Test
    void balancedIdenticalMetricsGivesZeroWithoutNaN() throws Exception {
        Recommendation rec = run("BALANCED", TestData.evaluate(List.of(TestData.rail(8, "2.80", 0.03, 0.9),
                TestData.rail(9, "2.80", 0.03, 0.9))));
        for (ScoreBreakdown s : rec.getScores().values()) {
            assertEquals(0.0, s.getTotalScore());
            assertFalse(Double.isNaN(s.getNormalizedCost()) || Double.isNaN(s.getNormalizedReliability()));
        }
        assertEquals(8L, rec.getChosen().getRouteId());
    }

    @Test
    void emptyInputGivesNoRecommendation() throws Exception {
        for (String key : KEYS) {
            Recommendation rec = run(key, List.of());
            assertFalse(rec.isRecommended(), key);
            assertNull(rec.getChosen());
            assertFalse(rec.getReason().isBlank());
        }
    }

    @ParameterizedTest
    @CsvSource({"-0.1, 0.5, 0.3, 0.3", "0.5, 0.5, 0.5, 0.5", "0.25, 0.25, 0.25, 0.24", "NaN, 0.5, 0.25, 0.25"})
    void invalidBalancedWeightsRejected(double c, double t, double e, double r) {
        assertThrows(IllegalArgumentException.class, () -> new BalancedStrategy(c, t, e, r));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "FOO", "cheap"})
    void invalidStrategyKeyRejected(String key) {
        assertThrows(InvalidStrategyException.class, () -> service.requireStrategy(key));
    }

    @Test
    void lowerCaseKeyAcceptedAndStrategiesListed() throws Exception {
        assertEquals("CHEAPEST", service.requireStrategy(" cheapest ").getKey());
        assertEquals("BALANCED", service.requireStrategy("balanced").getKey());
        List<String> keys = new ArrayList<>();
        for (OptimizationStrategy s : service.getStrategies()) {
            keys.add(s.getKey());
        }
        assertEquals(KEYS, keys);
    }
}
