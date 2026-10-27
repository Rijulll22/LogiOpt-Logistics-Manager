# LogiOpt formulas, rounding and assumptions

All route data is configured SAMPLE data. *Estimates based on configured sample rates and factors; not live carrier data.*

## Units
| Quantity | Unit | Java type |
|---|---|---|
| Money | INR | `BigDecimal` (scale 2) |
| Weight | kg (`tonnes = weightKg / 1000`) | `double` |
| Distance | km | `double` |
| Duration | hours | `Double` (null = not available) |
| Emissions | kg CO2e | `Double` (null = not available) |
| Rate | INR per tonne-km | `BigDecimal` |
| Emission factor | kg CO2e per tonne-km | `Double` (nullable) |
| Reliability | 0..1, 1 = most reliable | `Double` (nullable) |

## Cost (`CostService`, components rounded by `CostBreakdown`)
- transport = Σ over legs (tonnes × distanceKm × ratePerTonneKm)
- handling = route handling charge (once per shipment, never per leg)
- transfer = transferCount × transferChargePerTransfer, where transferCount = legs − 1 (so 0 for single-leg routes)
- waiting = waitingHours × waitingRatePerHour
- Each component is rounded to 2 dp HALF_UP **first**; total = sum of the rounded components (`CostBreakdown.getTotal()`, never stored).
  Example: 10.005 + 10.005 → 10.01 + 10.01 = 20.02.

## Duration (`DurationService`)
- hours = Σ over legs (distanceKm / speedKmh + bufferHours) + transferCount × transferHoursPerTransfer + waitingHours
- null if ANY leg has no speed; otherwise `Numbers.round2`.
- Arrival assumption: departure at 00:00 on the dispatch date; arrival = dispatch date 00:00 + `Math.round(hours × 60)` minutes.

## Emissions (`EmissionService`)
- kg = Σ over legs (tonnes × distanceKm × emissionFactor); null if ANY leg has no factor; otherwise `Numbers.round2`.

## Weight limits (`ShipmentValidator`)
- Weight must fit `shipments.weight_kg DECIMAL(12,3)`: at least 0.001 kg, at most 999,999,999.999 kg, at most 3 decimal places. The form value is checked exactly as typed (`BigDecimal`); nothing is rounded. Invalid weights are rejected with a message, and `ShipmentDAO` refuses to round on insert.

## Report formatting (`ReportService`)
- Money, duration and emissions: 2 decimals (`Numbers.format`, HALF_UP). Weight: 3 decimals and reliability: 3 decimals (their stored scales). Balanced scores: 6 decimals. Missing values print "not available".

## Rounding (`Numbers`)
- `round2(v) = BigDecimal.valueOf(v).setScale(2, HALF_UP).doubleValue()` (2.675 → 2.68, 1.005 → 1.01, −2.675 → −2.68); `round6` same with 6 dp; non-finite input throws `IllegalArgumentException`.
- A missing value is `null`; it is never treated, defaulted or compared as zero.

## Strategies
- Cheapest: lowest total cost. Fastest: lowest duration. Greenest: lowest emissions.
- A route lacking the strategy's required metric is excluded from that strategy only, with a reason (e.g. "Emissions not available"); it stays in the full comparison list.
- Tie-break (all strategies): primary metric, then total cost, then duration, then emissions, then routeId; nulls last.

## Balanced (`BalancedStrategy`)
- Requires all four metrics (cost, duration, emissions, reliability); otherwise excluded with the list of missing metrics.
- Min-max normalization over the scorable routes, using the STORED rounded values (2-dp duration/emissions, rounded total cost as double):
  - cost, time, emissions: n = (x − min) / (max − min)
  - reliability: n = (max − r) / (max − min)
  - if max == min, n = 0 for every route (no NaN).
- score = 0.35·nCost + 0.30·nTime + 0.20·nEmissions + 0.15·nReliability, rounded to 6 dp; **lowest score wins**. Custom weights must each be ≥ 0 and sum to 1.0 (±1e-9).
- Scores are relative to the routes currently compared; adding or removing a route changes them.

## Verified example (Mumbai → Delhi, 5000 kg, dispatch 2026-11-02)
| Route | Total INR | Hours | kg CO2e | Balanced (routes 1-3) | Balanced (routes 1-4) |
|---|---|---|---|---|---|
| 1 Road direct | 33000.00 | 30.00 | 630.00 | 0.625000 | 0.625000 |
| 2 Rail direct | 22800.00 | 38.22 | 217.50 | 0.138074 | 0.167971 |
| 3 Road-Rail-Road | 26075.00 | 47.86 | 259.50 | 0.582741 | 0.609594 |
| 4 Rail electric | 25275.00 | 44.25 | 145.00 | - | 0.361788 |

Routes 1-4 winners: Cheapest R2, Fastest R1, Greenest R4, Balanced R2.
