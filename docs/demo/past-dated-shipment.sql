-- LogiOpt demo data: one past-dated shipment, for the demo "a saved past-dated shipment can still
-- be compared" (Review 3).
--
-- NOT A MIGRATION. This file is outside the classpath and the WAR, and nothing in the application
-- runs it. Run it MANUALLY, against the database you have chosen for the demo, for example from a
-- MySQL client connected to that database:
--     source docs/demo/past-dated-shipment.sql
-- or open it in MySQL Workbench with that database selected and execute it.
--
-- Before running:
--   * the chosen database must already be migrated to the application's current version
--     (MigrationTool);
--   * the customer must already be registered in the application; change the email on the SET
--     line below to that customer's email.
-- If the email does not belong to an enabled customer, nothing is inserted. Running the script
-- again does not insert a second copy (the row is recognised by its "DEMO past-dated shipment"
-- dimensions text).
--
-- What it inserts: a DRAFT shipment Mumbai -> Delhi, GENERAL cargo, 5000 kg, dispatch date 30 days
-- before today. It can still be compared (saved shipments are planned even when their date has
-- passed), but it cannot be booked (booking needs a dispatch date of today or later) and cannot be
-- saved through the edit form (editing uses the same rules as creating a shipment).

SET @customer_email = 'customer@example.com';

INSERT INTO shipments (user_id, source, destination, cargo_type, weight_kg, dimensions, dispatch_date, status)
SELECT u.user_id, 'Mumbai', 'Delhi', 'GENERAL', 5000.000, 'DEMO past-dated shipment',
       CURRENT_DATE - INTERVAL 30 DAY, 'DRAFT'
FROM users u
WHERE u.email = LOWER(TRIM(@customer_email)) AND u.role = 'CUSTOMER' AND u.enabled = TRUE
  AND NOT EXISTS (SELECT 1 FROM shipments s WHERE s.user_id = u.user_id AND s.dimensions = 'DEMO past-dated shipment');

SELECT s.shipment_id, u.email, s.source, s.destination, s.dispatch_date, s.status
FROM shipments s JOIN users u ON u.user_id = s.user_id
WHERE u.email = LOWER(TRIM(@customer_email)) AND s.dimensions = 'DEMO past-dated shipment';
