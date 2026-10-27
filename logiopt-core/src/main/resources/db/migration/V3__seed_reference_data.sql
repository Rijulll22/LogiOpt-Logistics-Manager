-- V3: SAMPLE reference data. Estimates based on configured sample rates and factors; not live carrier data.
-- Idempotent upserts only; nothing is deleted, so it never breaks foreign keys from shipments or
-- bookings. Routes 1-10 and their legs (matched on uk_route_legs_seq from V2) are set to the values
-- below; an existing user 1 is left unchanged.

-- Demo user (id 1) that owns the Review 2 shipments. Disabled; the hash is a deliberately invalid placeholder.
INSERT INTO users (user_id, name, email, password_hash, role, enabled)
VALUES (1, 'Demo User', 'demo@logiopt.invalid', 'DISABLED-PLACEHOLDER-NOT-A-HASH', 'CUSTOMER', FALSE) AS new_user
ON DUPLICATE KEY UPDATE user_id = users.user_id;

INSERT INTO routes (route_id, name, source, destination, mode, reliability, capacity_kg, handling_charge,
                    transfer_charge_per_transfer, transfer_hours_per_transfer, waiting_hours,
                    waiting_rate_per_hour, unsupported_cargo)
VALUES
-- SEED-CORE-4: Mumbai -> Delhi (identical to the verified test data)
(1, 'Road direct', 'Mumbai', 'Delhi', 'ROAD', 0.800, 20000, 1500, 0, 0, 0, 0, ''),
(2, 'Rail direct', 'Mumbai', 'Delhi', 'RAIL', 0.900, 30000, 2500, 0, 0, 0, 0, ''),
(3, 'Road-Rail-Road', 'Mumbai', 'Delhi', 'MULTIMODAL', 0.700, 15000, 2000, 800, 3, 4, 100, ''),
(4, 'Rail electric', 'Mumbai', 'Delhi', 'RAIL', 0.850, 30000, 2800, 0, 0, 0, 0, ''),
-- Chennai -> Kolkata (route 6 rail does not support HAZARDOUS)
(5, 'Road express', 'Chennai', 'Kolkata', 'ROAD', 0.780, 20000, 1400, 0, 0, 0, 0, ''),
(6, 'Rail freight', 'Chennai', 'Kolkata', 'RAIL', 0.880, 30000, 2400, 0, 0, 0, 0, 'HAZARDOUS'),
(7, 'Road-Rail-Road', 'Chennai', 'Kolkata', 'MULTIMODAL', 0.720, 15000, 1900, 750, 3, 3, 100, ''),
-- Ahmedabad -> Bengaluru (route 9 rail has no configured emission factor)
(8, 'Road express', 'Ahmedabad', 'Bengaluru', 'ROAD', 0.800, 20000, 1450, 0, 0, 0, 0, ''),
(9, 'Rail freight', 'Ahmedabad', 'Bengaluru', 'RAIL', 0.870, 30000, 2450, 0, 0, 0, 0, ''),
(10, 'Road-Rail-Road', 'Ahmedabad', 'Bengaluru', 'MULTIMODAL', 0.740, 15000, 2100, 800, 3, 4, 100, '') AS r
ON DUPLICATE KEY UPDATE name = r.name, source = r.source, destination = r.destination, mode = r.mode,
                        reliability = r.reliability, capacity_kg = r.capacity_kg,
                        handling_charge = r.handling_charge,
                        transfer_charge_per_transfer = r.transfer_charge_per_transfer,
                        transfer_hours_per_transfer = r.transfer_hours_per_transfer,
                        waiting_hours = r.waiting_hours, waiting_rate_per_hour = r.waiting_rate_per_hour,
                        unsupported_cargo = r.unsupported_cargo;

INSERT INTO route_legs (route_id, sequence_no, mode, distance_km, rate_per_tonne_km, speed_kmh, buffer_hours,
                        emission_factor)
VALUES
(1, 1, 'ROAD', 1400, 4.50, 50, 2.0, 0.09),
(2, 1, 'RAIL', 1450, 2.80, 45, 6.0, 0.03),
(3, 1, 'ROAD', 60, 4.50, 40, 0, 0.09),
(3, 2, 'RAIL', 1400, 2.80, 45, 4.0, 0.03),
(3, 3, 'ROAD', 50, 4.50, 40, 0, 0.09),
(4, 1, 'RAIL', 1450, 3.10, 40, 8.0, 0.02),
(5, 1, 'ROAD', 1670, 4.60, 50, 3.0, 0.09),
(6, 1, 'RAIL', 1660, 2.70, 42, 6.0, 0.03),
(7, 1, 'ROAD', 40, 4.60, 40, 0, 0.09),
(7, 2, 'RAIL', 1600, 2.70, 42, 4.0, 0.03),
(7, 3, 'ROAD', 35, 4.60, 40, 0, 0.09),
(8, 1, 'ROAD', 1500, 4.40, 52, 2.5, 0.09),
(9, 1, 'RAIL', 1550, 2.75, 44, 7.0, NULL),
(10, 1, 'ROAD', 30, 4.40, 40, 0, 0.09),
(10, 2, 'RAIL', 1480, 2.75, 44, 5.0, 0.03),
(10, 3, 'ROAD', 45, 4.40, 40, 0, 0.09) AS l
ON DUPLICATE KEY UPDATE mode = l.mode, distance_km = l.distance_km, rate_per_tonne_km = l.rate_per_tonne_km,
                        speed_kmh = l.speed_kmh, buffer_hours = l.buffer_hours,
                        emission_factor = l.emission_factor;
