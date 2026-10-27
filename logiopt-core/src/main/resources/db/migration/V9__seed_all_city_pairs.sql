-- V9: ILLUSTRATIVE SAMPLE route data for every ordered pair of the six seeded cities. Distances are
-- illustrative round figures and the rates, speeds and factors are the V3 Mumbai -> Delhi sample
-- constants; none of this is real carrier data or a real tariff.
--
-- Every ordered pair of distinct cities that has no route yet (on a standard install: all pairs
-- except V3's Mumbai -> Delhi, Chennai -> Kolkata and Ahmedabad -> Bengaluru) gets three routes:
--   kind 1 'Road direct'    ROAD        one road leg of the road distance
--   kind 2 'Rail direct'    RAIL        one rail leg of the rail distance; refuses HAZARDOUS cargo
--   kind 3 'Road-Rail-Road' MULTIMODAL  road 100 km, rail (rail distance - 200 km), road 100 km
-- Road distances are the same in both directions; rail distance = ROUND(road km * 1.05), computed in
-- exact decimal arithmetic, so a half km rounds up (for example 530 -> 556.5 -> 557).
--
-- Constants (copied from V3 routes 1-3, Mumbai -> Delhi):
--   Road direct:    reliability 0.800, capacity 20000 kg, handling 1500;
--                   road leg 4.50 per tonne-km, 50 km/h, buffer 2.0 h, 0.09 kg CO2e per tonne-km
--   Rail direct:    reliability 0.900, capacity 30000 kg, handling 2500;
--                   rail leg 2.80 per tonne-km, 45 km/h, buffer 6.0 h, 0.03
--   Road-Rail-Road: reliability 0.700, capacity 15000 kg, handling 2000, transfer 800 per transfer,
--                   3 h per transfer, waiting 4 h at 100 per hour;
--                   road legs 4.50, 40 km/h, buffer 0, 0.09; rail trunk 2.80, 45 km/h, buffer 4.0, 0.03
--
-- Route ids are fixed by the data: 1000 + 100 * source code + 10 * destination code + kind, with
-- city codes Ahmedabad 1, Bengaluru 2, Chennai 3, Delhi 4, Kolkata 5, Mumbai 6
-- (example: Delhi -> Mumbai Rail direct = 1000 + 400 + 60 + 2 = 1462).
--
-- Idempotent: corridors that already have a route are skipped, legs are added only to a route
-- that has the generated id, source, destination and name, and a duplicate key leaves the
-- existing row as it is. Nothing is updated or deleted; routes 1-10 and their legs are never touched.
-- The distance list appears in both statements and must stay identical (a test checks the result).

INSERT INTO routes (route_id, name, source, destination, mode, reliability, capacity_kg, handling_charge,
                    transfer_charge_per_transfer, transfer_hours_per_transfer, waiting_hours,
                    waiting_rate_per_hour, unsupported_cargo)
WITH city (name, code) AS (
         VALUES ROW('Ahmedabad', 1), ROW('Bengaluru', 2), ROW('Chennai', 3), ROW('Delhi', 4),
                ROW('Kolkata', 5), ROW('Mumbai', 6)),
     distance (city_a, city_b, road_km) AS (
         VALUES ROW('Mumbai', 'Delhi', 1400), ROW('Mumbai', 'Ahmedabad', 530), ROW('Mumbai', 'Bengaluru', 980),
                ROW('Mumbai', 'Chennai', 1330), ROW('Mumbai', 'Kolkata', 2000), ROW('Delhi', 'Ahmedabad', 940),
                ROW('Delhi', 'Bengaluru', 2150), ROW('Delhi', 'Chennai', 2180), ROW('Delhi', 'Kolkata', 1500),
                ROW('Ahmedabad', 'Bengaluru', 1490), ROW('Ahmedabad', 'Chennai', 1830),
                ROW('Ahmedabad', 'Kolkata', 2050), ROW('Bengaluru', 'Chennai', 350),
                ROW('Bengaluru', 'Kolkata', 1870), ROW('Chennai', 'Kolkata', 1670)),
     corridor (source, destination) AS (
         SELECT city_a, city_b FROM distance UNION ALL SELECT city_b, city_a FROM distance),
     kind (kind, name, mode, reliability, capacity_kg, handling_charge, transfer_charge, transfer_hours,
           waiting_hours, waiting_rate, unsupported_cargo) AS (
         VALUES ROW(1, 'Road direct', 'ROAD', 0.800, 20000, 1500, 0, 0, 0, 0, ''),
                ROW(2, 'Rail direct', 'RAIL', 0.900, 30000, 2500, 0, 0, 0, 0, 'HAZARDOUS'),
                ROW(3, 'Road-Rail-Road', 'MULTIMODAL', 0.700, 15000, 2000, 800, 3, 4, 100, ''))
SELECT 1000 + 100 * s.code + 10 * d.code + k.kind, k.name, c.source, c.destination, k.mode, k.reliability,
       k.capacity_kg, k.handling_charge, k.transfer_charge, k.transfer_hours, k.waiting_hours, k.waiting_rate,
       k.unsupported_cargo
FROM corridor c
JOIN city s ON s.name = c.source
JOIN city d ON d.name = c.destination
CROSS JOIN kind k
WHERE NOT EXISTS (SELECT 1 FROM routes x WHERE x.source = c.source AND x.destination = c.destination)
ORDER BY 1
ON DUPLICATE KEY UPDATE route_id = routes.route_id;

INSERT INTO route_legs (route_id, sequence_no, mode, distance_km, rate_per_tonne_km, speed_kmh, buffer_hours,
                        emission_factor)
WITH city (name, code) AS (
         VALUES ROW('Ahmedabad', 1), ROW('Bengaluru', 2), ROW('Chennai', 3), ROW('Delhi', 4),
                ROW('Kolkata', 5), ROW('Mumbai', 6)),
     distance (city_a, city_b, road_km) AS (
         VALUES ROW('Mumbai', 'Delhi', 1400), ROW('Mumbai', 'Ahmedabad', 530), ROW('Mumbai', 'Bengaluru', 980),
                ROW('Mumbai', 'Chennai', 1330), ROW('Mumbai', 'Kolkata', 2000), ROW('Delhi', 'Ahmedabad', 940),
                ROW('Delhi', 'Bengaluru', 2150), ROW('Delhi', 'Chennai', 2180), ROW('Delhi', 'Kolkata', 1500),
                ROW('Ahmedabad', 'Bengaluru', 1490), ROW('Ahmedabad', 'Chennai', 1830),
                ROW('Ahmedabad', 'Kolkata', 2050), ROW('Bengaluru', 'Chennai', 350),
                ROW('Bengaluru', 'Kolkata', 1870), ROW('Chennai', 'Kolkata', 1670)),
     corridor (source, destination, road_km, rail_km) AS (
         SELECT city_a, city_b, road_km, ROUND(road_km * 1.05) FROM distance
         UNION ALL SELECT city_b, city_a, road_km, ROUND(road_km * 1.05) FROM distance),
     leg (kind, route_name, sequence_no, mode, basis, rate, speed, buffer_hours, factor) AS (
         VALUES ROW(1, 'Road direct', 1, 'ROAD', 'ROAD', 4.50, 50, 2.0, 0.09),
                ROW(2, 'Rail direct', 1, 'RAIL', 'RAIL', 2.80, 45, 6.0, 0.03),
                ROW(3, 'Road-Rail-Road', 1, 'ROAD', 'END', 4.50, 40, 0, 0.09),
                ROW(3, 'Road-Rail-Road', 2, 'RAIL', 'TRUNK', 2.80, 45, 4.0, 0.03),
                ROW(3, 'Road-Rail-Road', 3, 'ROAD', 'END', 4.50, 40, 0, 0.09))
SELECT r.route_id, l.sequence_no, l.mode,
       CASE l.basis WHEN 'ROAD' THEN c.road_km WHEN 'RAIL' THEN c.rail_km WHEN 'TRUNK' THEN c.rail_km - 200
                    ELSE 100 END,
       l.rate, l.speed, l.buffer_hours, l.factor
FROM corridor c
JOIN city s ON s.name = c.source
JOIN city d ON d.name = c.destination
CROSS JOIN leg l
JOIN routes r ON r.route_id = 1000 + 100 * s.code + 10 * d.code + l.kind AND r.source = c.source
             AND r.destination = c.destination AND r.name = l.route_name
ORDER BY r.route_id, l.sequence_no
ON DUPLICATE KEY UPDATE route_id = route_legs.route_id;
