package com.logiopt.model;

import com.logiopt.TestData;
import com.logiopt.exception.RouteConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.logiopt.TestData.leg;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteValidationTest {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final TransportMode ROAD = TransportMode.ROAD;
    private static final TransportMode RAIL = TransportMode.RAIL;

    static Stream<Arguments> invalidLegs() {
        return Stream.of(
                Arguments.of("null mode", (Executable) () -> new RouteLeg(null, 10, ONE, 50.0, 0, 0.1)),
                Arguments.of("multimodal mode",
                        (Executable) () -> new RouteLeg(TransportMode.MULTIMODAL, 10, ONE, 50.0, 0, 0.1)),
                Arguments.of("distance 0", (Executable) () -> new RouteLeg(ROAD, 0, ONE, 50.0, 0, 0.1)),
                Arguments.of("distance negative", (Executable) () -> new RouteLeg(ROAD, -1, ONE, 50.0, 0, 0.1)),
                Arguments.of("distance NaN", (Executable) () -> new RouteLeg(ROAD, Double.NaN, ONE, 50.0, 0, 0.1)),
                Arguments.of("distance infinite",
                        (Executable) () -> new RouteLeg(ROAD, Double.POSITIVE_INFINITY, ONE, 50.0, 0, 0.1)),
                Arguments.of("rate null", (Executable) () -> new RouteLeg(ROAD, 10, null, 50.0, 0, 0.1)),
                Arguments.of("rate negative",
                        (Executable) () -> new RouteLeg(ROAD, 10, new BigDecimal("-0.01"), 50.0, 0, 0.1)),
                Arguments.of("speed 0", (Executable) () -> new RouteLeg(ROAD, 10, ONE, 0.0, 0, 0.1)),
                Arguments.of("speed negative", (Executable) () -> new RouteLeg(ROAD, 10, ONE, -5.0, 0, 0.1)),
                Arguments.of("speed NaN", (Executable) () -> new RouteLeg(ROAD, 10, ONE, Double.NaN, 0, 0.1)),
                Arguments.of("speed infinite",
                        (Executable) () -> new RouteLeg(ROAD, 10, ONE, Double.POSITIVE_INFINITY, 0, 0.1)),
                Arguments.of("buffer negative", (Executable) () -> new RouteLeg(ROAD, 10, ONE, 50.0, -1, 0.1)),
                Arguments.of("buffer NaN", (Executable) () -> new RouteLeg(ROAD, 10, ONE, 50.0, Double.NaN, 0.1)),
                Arguments.of("factor negative", (Executable) () -> new RouteLeg(ROAD, 10, ONE, 50.0, 0, -0.1)),
                Arguments.of("factor NaN", (Executable) () -> new RouteLeg(ROAD, 10, ONE, 50.0, 0, Double.NaN)),
                Arguments.of("factor infinite",
                        (Executable) () -> new RouteLeg(ROAD, 10, ONE, 50.0, 0, Double.POSITIVE_INFINITY)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLegs")
    void invalidLegRejected(String name, Executable construction) {
        assertThrows(RouteConfigurationException.class, construction);
    }

    @Test
    void legAllowsMissingSpeedAndFactor() {
        RouteLeg leg = assertDoesNotThrow(() -> new RouteLeg(RAIL, 10, BigDecimal.ZERO, null, 0, null));
        assertEquals(null, leg.getSpeedKmh());
        assertEquals(null, leg.getEmissionFactorKgPerTonneKm());
    }

    private static RouteParams validRoad() {
        return TestData.params(1, "Road", leg(ROAD, 100, "1.00", 50.0, 0, 0.1))
                .handlingCharge(ONE).reliability(0.5).capacityKg(1000);
    }

    static Stream<Arguments> invalidRouteParams() {
        return Stream.of(
                Arguments.of("routeId 0", (UnaryOperator<RouteParams>) p -> p.routeId(0)),
                Arguments.of("routeId negative", (UnaryOperator<RouteParams>) p -> p.routeId(-1)),
                Arguments.of("name null", (UnaryOperator<RouteParams>) p -> p.name(null)),
                Arguments.of("name blank", (UnaryOperator<RouteParams>) p -> p.name("  ")),
                Arguments.of("source blank", (UnaryOperator<RouteParams>) p -> p.source("")),
                Arguments.of("destination null", (UnaryOperator<RouteParams>) p -> p.destination(null)),
                Arguments.of("legs null", (UnaryOperator<RouteParams>) p -> p.legs(null)),
                Arguments.of("legs empty", (UnaryOperator<RouteParams>) p -> p.legs(List.of())),
                Arguments.of("legs with null", (UnaryOperator<RouteParams>) p ->
                        p.legs(Arrays.asList(leg(ROAD, 100, "1.00", 50.0, 0, 0.1), null))),
                Arguments.of("handling null", (UnaryOperator<RouteParams>) p -> p.handlingCharge(null)),
                Arguments.of("handling negative",
                        (UnaryOperator<RouteParams>) p -> p.handlingCharge(new BigDecimal("-1"))),
                Arguments.of("transfer charge null",
                        (UnaryOperator<RouteParams>) p -> p.transferChargePerTransfer(null)),
                Arguments.of("transfer charge negative",
                        (UnaryOperator<RouteParams>) p -> p.transferChargePerTransfer(new BigDecimal("-1"))),
                Arguments.of("waiting rate null", (UnaryOperator<RouteParams>) p -> p.waitingRatePerHour(null)),
                Arguments.of("waiting rate negative",
                        (UnaryOperator<RouteParams>) p -> p.waitingRatePerHour(new BigDecimal("-1"))),
                Arguments.of("transfer hours negative",
                        (UnaryOperator<RouteParams>) p -> p.transferHoursPerTransfer(-1)),
                Arguments.of("transfer hours NaN",
                        (UnaryOperator<RouteParams>) p -> p.transferHoursPerTransfer(Double.NaN)),
                Arguments.of("waiting hours negative", (UnaryOperator<RouteParams>) p -> p.waitingHours(-1)),
                Arguments.of("waiting hours infinite",
                        (UnaryOperator<RouteParams>) p -> p.waitingHours(Double.POSITIVE_INFINITY)),
                Arguments.of("capacity 0", (UnaryOperator<RouteParams>) p -> p.capacityKg(0)),
                Arguments.of("capacity NaN", (UnaryOperator<RouteParams>) p -> p.capacityKg(Double.NaN)),
                Arguments.of("reliability below 0", (UnaryOperator<RouteParams>) p -> p.reliability(-0.1)),
                Arguments.of("reliability above 1", (UnaryOperator<RouteParams>) p -> p.reliability(1.1)),
                Arguments.of("reliability NaN", (UnaryOperator<RouteParams>) p -> p.reliability(Double.NaN)),
                Arguments.of("unsupported cargo null", (UnaryOperator<RouteParams>) p -> p.unsupportedCargo(null)),
                Arguments.of("unsupported cargo with null",
                        (UnaryOperator<RouteParams>) p -> p.unsupportedCargo(Arrays.asList((CargoType) null))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRouteParams")
    void invalidRouteRejected(String name, UnaryOperator<RouteParams> change) {
        RouteParams params = change.apply(validRoad());
        assertThrows(RouteConfigurationException.class, () -> RouteFactory.create(ROAD, params));
    }

    static Stream<Arguments> invalidStructures() {
        RouteLeg road = leg(ROAD, 100, "1.00", 50.0, 0, 0.1);
        RouteLeg rail = leg(RAIL, 100, "1.00", 50.0, 0, 0.1);
        return Stream.of(
                Arguments.of("road with rail leg", ROAD, List.of(rail)),
                Arguments.of("road with two legs", ROAD, List.of(road, road)),
                Arguments.of("rail with road leg", RAIL, List.of(road)),
                Arguments.of("rail with two legs", RAIL, List.of(rail, rail)),
                Arguments.of("multimodal with one leg", TransportMode.MULTIMODAL, List.of(road)),
                Arguments.of("multimodal with one mode", TransportMode.MULTIMODAL, List.of(road, road)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStructures")
    void invalidStructureRejected(String name, TransportMode mode, List<RouteLeg> legs) {
        RouteParams params = validRoad().legs(legs);
        assertThrows(RouteConfigurationException.class, () -> RouteFactory.create(mode, params));
    }

    @Test
    void nullParamsAndNullModeRejected() {
        assertThrows(RouteConfigurationException.class, () -> RouteFactory.create(ROAD, null));
        assertThrows(RouteConfigurationException.class, () -> RouteFactory.create(null, validRoad()));
    }

    @Test
    void factoryBuildsSubclassesAndStructure() {
        assertInstanceOf(RoadRoute.class, TestData.route1());
        assertInstanceOf(RailRoute.class, TestData.route2());
        Route multimodal = TestData.route3();
        assertInstanceOf(MultimodalRoute.class, multimodal);
        assertEquals(TransportMode.MULTIMODAL, multimodal.getMode());
        assertEquals(2, multimodal.getTransferCount());
        assertEquals(0, TestData.route1().getTransferCount());
        assertEquals(1510.0, multimodal.getTotalDistanceKm(), TestData.EPS);
        assertThrows(UnsupportedOperationException.class, () -> multimodal.getLegs().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> multimodal.getUnsupportedCargo().add(CargoType.HAZARDOUS));
    }

    @Test
    void reliabilityBoundsAndCargoCopyAccepted() {
        assertDoesNotThrow(() -> RouteFactory.create(ROAD, validRoad().reliability(0.0)));
        assertDoesNotThrow(() -> RouteFactory.create(ROAD, validRoad().reliability(1.0)));
        assertDoesNotThrow(() -> RouteFactory.create(ROAD, validRoad().reliability(null)));
        Route route = RouteFactory.create(ROAD, validRoad().unsupportedCargo(Set.of(CargoType.HAZARDOUS)));
        assertEquals(Set.of(CargoType.HAZARDOUS), route.getUnsupportedCargo());
    }
}
