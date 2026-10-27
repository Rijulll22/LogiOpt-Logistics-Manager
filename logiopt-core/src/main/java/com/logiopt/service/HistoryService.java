package com.logiopt.service;

import com.logiopt.dao.BookingDAO;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.exception.DataAccessException;
import com.logiopt.model.Booking;
import com.logiopt.model.Shipment;
import com.logiopt.model.User;
import com.logiopt.report.ReportRecord;
import com.logiopt.report.ReportService;

import java.util.List;

/**
 * The signed-in user's latest shipments, bookings and reports, newest first, at most
 * {@link #LIMIT} of each.
 */
public class HistoryService {

    /** Maximum number of entries per list. */
    public static final int LIMIT = 25;

    private final ShipmentDAO shipments;
    private final BookingDAO bookings;
    private final ReportService reports;

    /** One user's history. Each list holds at most {@link #LIMIT} entries. */
    public static final class History {
        private final List<Shipment> shipments;
        private final List<Booking> bookings;
        private final List<ReportRecord> reports;
        private final boolean moreShipments;
        private final boolean moreBookings;
        private final boolean moreReports;

        History(List<Shipment> shipments, List<Booking> bookings, List<ReportRecord> reports) {
            this.moreShipments = shipments.size() > LIMIT;
            this.moreBookings = bookings.size() > LIMIT;
            this.moreReports = reports.size() > LIMIT;
            this.shipments = List.copyOf(moreShipments ? shipments.subList(0, LIMIT) : shipments);
            this.bookings = List.copyOf(moreBookings ? bookings.subList(0, LIMIT) : bookings);
            this.reports = List.copyOf(moreReports ? reports.subList(0, LIMIT) : reports);
        }

        /** @return latest shipments, newest first */
        public List<Shipment> getShipments() {
            return shipments;
        }

        /** @return latest bookings, newest first */
        public List<Booking> getBookings() {
            return bookings;
        }

        /** @return latest report jobs, newest first */
        public List<ReportRecord> getReports() {
            return reports;
        }

        /** @return true if older shipments exist that are not shown */
        public boolean isMoreShipments() {
            return moreShipments;
        }

        /** @return true if older bookings exist that are not shown */
        public boolean isMoreBookings() {
            return moreBookings;
        }

        /** @return true if older reports exist that are not shown */
        public boolean isMoreReports() {
            return moreReports;
        }

        /** @return the per-list limit */
        public int getLimit() {
            return LIMIT;
        }
    }

    /**
     * Creates the service.
     *
     * @param shipments shipment DAO
     * @param bookings  booking DAO
     * @param reports   report jobs
     */
    public HistoryService(ShipmentDAO shipments, BookingDAO bookings, ReportService reports) {
        if (shipments == null || bookings == null || reports == null) {
            throw new IllegalArgumentException("History dependencies must not be null");
        }
        this.shipments = shipments;
        this.bookings = bookings;
        this.reports = reports;
    }

    /**
     * @param user signed-in user
     * @return the user's own history
     * @throws DataAccessException on database failure
     */
    public History of(User user) throws DataAccessException {
        long id = user.getUserId();
        return new History(shipments.findRecentByUser(id, LIMIT + 1), bookings.findRecentByOwner(id, LIMIT + 1),
                reports.history(id, LIMIT + 1));
    }
}
