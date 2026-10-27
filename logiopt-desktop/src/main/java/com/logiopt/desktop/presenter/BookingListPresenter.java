package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.BookingRow;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.Booking;
import com.logiopt.model.User;
import com.logiopt.service.BookingService;

import java.util.ArrayList;
import java.util.List;

/** "My bookings": the signed-in user's own bookings (BookingService filters by owner). */
public final class BookingListPresenter {

    /** What the bookings page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param rows the user's bookings */
        void showBookings(List<BookingRow> rows);

        /** @param message error state text */
        void showError(String message);
    }

    private final BookingService bookings;
    private final BackgroundRunner runner;
    private final User user;
    private final View view;
    private final boolean all;

    /**
     * @param bookings booking service
     * @param runner   background runner
     * @param user     signed-in user
     * @param view     page
     */
    public BookingListPresenter(BookingService bookings, BackgroundRunner runner, User user, View view) {
        this(bookings, runner, user, view, false);
    }

    private BookingListPresenter(BookingService bookings, BackgroundRunner runner, User user, View view, boolean all) {
        this.bookings = bookings;
        this.runner = runner;
        this.user = user;
        this.view = view;
        this.all = all;
    }

    /**
     * Every booking, read-only, for the administrator ({@link BookingService#allBookings} refuses
     * anyone else, and so does this presenter).
     *
     * @param bookings booking service
     * @param runner   background runner
     * @param admin    signed-in user (must be the administrator)
     * @param view     page
     * @return the presenter
     */
    public static BookingListPresenter allBookings(BookingService bookings, BackgroundRunner runner, User admin,
                                                   View view) {
        return new BookingListPresenter(bookings, runner, admin, view, true);
    }

    /** Loads the bookings in the background. */
    public void load() {
        if (all && !user.isAdmin()) {
            view.showError(ErrorMessages.NOT_FOUND);
            return;
        }
        view.showLoading();
        runner.run(() -> {
            List<BookingRow> rows = new ArrayList<>();
            for (Booking b : all ? bookings.allBookings(user) : bookings.myBookings(user)) {
                rows.add(ViewModels.bookingRow(b));
            }
            return rows;
        }, view::showBookings, error -> view.showError(ErrorMessages.of(error)));
    }
}
