package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.DashboardModel;
import com.logiopt.desktop.presenter.Models.HistoryModel;
import com.logiopt.desktop.presenter.Models.ShipmentFormModel;
import com.logiopt.desktop.presenter.Models.ShipmentRow;
import com.logiopt.desktop.support.BackgroundRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test doubles for presenter tests: a runner that runs work immediately but marks it as background
 * work, and fake views that record every call and fail if a view is touched from background work.
 */
final class Fakes {

    private Fakes() {
    }

    /** Runs work at once, flagging it as background work so views can check they are not called from it. */
    static final class CheckingRunner implements BackgroundRunner {
        private static final ThreadLocal<Boolean> IN_BACKGROUND = ThreadLocal.withInitial(() -> false);
        int tasks;

        static void assertNotInBackground() {
            assertFalse(IN_BACKGROUND.get(), "a view was called from background work");
        }

        @Override
        public <T> void run(Work<T> work, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure) {
            tasks++;
            T result;
            IN_BACKGROUND.set(true);
            try {
                result = work.call();
            } catch (Exception | Error e) {
                IN_BACKGROUND.set(false);
                onFailure.accept(e);
                return;
            } finally {
                IN_BACKGROUND.set(false);
            }
            onSuccess.accept(result);
        }

        @Override
        public void shutdown() {
        }
    }

    /** Records calls as text. */
    abstract static class Recorder {
        final List<String> calls = new ArrayList<>();

        void record(String call) {
            CheckingRunner.assertNotInBackground();
            calls.add(call);
        }

        String last() {
            return calls.isEmpty() ? null : calls.get(calls.size() - 1);
        }
    }

    static final class LoginView extends Recorder implements LoginPresenter.View {
        @Override
        public void setBusy(boolean busy) {
            record("busy " + busy);
        }

        @Override
        public void showError(String message) {
            record("error " + message);
        }
    }

    static final class RegisterView extends Recorder implements RegisterPresenter.View {
        Map<String, List<String>> errors = Map.of();

        @Override
        public void setBusy(boolean busy) {
            record("busy " + busy);
        }

        @Override
        public void showErrors(Map<String, List<String>> value) {
            record("errors " + value);
            errors = value;
        }
    }

    static final class DashboardView extends Recorder implements DashboardPresenter.View {
        DashboardModel model;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showDashboard(DashboardModel value) {
            record("dashboard");
            model = value;
        }

        @Override
        public void showError(String message) {
            record("error " + message);
        }
    }

    static final class FormView extends Recorder implements ShipmentFormPresenter.View {
        ShipmentFormModel model;
        Map<String, List<String>> errors = Map.of();
        String unavailable;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showForm(ShipmentFormModel value) {
            record("form");
            model = value;
        }

        @Override
        public void showFieldErrors(Map<String, List<String>> value) {
            record("errors " + value);
            errors = value;
        }

        @Override
        public void setBusy(boolean busy) {
            record("busy " + busy);
        }

        @Override
        public void showUnavailable(String message) {
            record("unavailable " + message);
            unavailable = message;
        }
    }

    static final class ListView extends Recorder implements ShipmentListPresenter.View {
        List<ShipmentRow> rows;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showShipments(List<ShipmentRow> value) {
            record("rows " + value.size());
            rows = value;
        }

        @Override
        public void showError(String message) {
            record("error " + message);
        }
    }

    static final class HistoryView extends Recorder implements HistoryPresenter.View {
        HistoryModel model;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showHistory(HistoryModel value) {
            record("history");
            model = value;
        }

        @Override
        public void showError(String message) {
            record("error " + message);
        }
    }

    static final class AccountView extends Recorder implements AccountPresenter.View {
        Models.AccountModel model;
        List<String> nameErrors = List.of();
        String nameNotice;
        Map<String, List<String>> passwordErrors = Map.of();
        String passwordNotice;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showAccount(Models.AccountModel value) {
            record("account " + value);
            model = value;
        }

        @Override
        public void showError(String message) {
            record("error " + message);
        }

        @Override
        public void showNameErrors(List<String> errors) {
            record("nameErrors " + errors);
            nameErrors = errors;
        }

        @Override
        public void showNameSaved(String notice) {
            record("nameSaved " + notice);
            nameNotice = notice;
        }

        @Override
        public void showPasswordErrors(Map<String, List<String>> errors) {
            record("passwordErrors " + errors);
            passwordErrors = errors;
        }

        @Override
        public void showPasswordChanged(String notice) {
            record("passwordChanged " + notice);
            passwordNotice = notice;
        }

        @Override
        public void setBusy(boolean busy) {
            record("busy " + busy);
        }
    }

    static final class ComparisonView extends Recorder implements ComparisonPresenter.View {
        Models.ComparisonModel model;
        Models.OrderSummaryModel order;
        String bookingError;
        String error;
        int busyOn;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showComparison(Models.ComparisonModel value) {
            record("comparison");
            model = value;
        }

        @Override
        public void showError(String message) {
            record("error " + message);
            error = message;
        }

        @Override
        public void showOrderSummary(Models.OrderSummaryModel value) {
            record("order " + value.routeId());
            order = value;
        }

        @Override
        public void setBookingBusy(boolean busy) {
            record("busy " + busy);
            if (busy) {
                busyOn++;
            }
        }

        @Override
        public void showBookingError(String message) {
            record("bookingError " + message);
            bookingError = message;
        }
    }

    static final class BookingView extends Recorder implements BookingPresenter.View {
        Models.BookingDetailModel model;
        String error;
        Boolean paid;
        String paymentMessage;
        String actionMessage;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showBooking(Models.BookingDetailModel value) {
            record("booking " + value.status());
            model = value;
        }

        @Override
        public void showError(String message) {
            record("error " + message);
            error = message;
        }

        @Override
        public void setBusy(boolean busy) {
            record("busy " + busy);
        }

        @Override
        public void showPaymentResult(boolean success, String message) {
            record("payment " + success);
            paid = success;
            paymentMessage = message;
        }

        @Override
        public void showActionMessage(String message) {
            record("action " + message);
            actionMessage = message;
        }
    }

    static final class BookingListView extends Recorder implements BookingListPresenter.View {
        List<Models.BookingRow> rows;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showBookings(List<Models.BookingRow> value) {
            record("rows " + value.size());
            rows = value;
        }

        @Override
        public void showError(String message) {
            record("error " + message);
        }
    }

    /** Keeps background work until {@link #runAll()}, to test what happens while a call is still running. */
    static final class DeferredRunner implements BackgroundRunner {
        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public <T> void run(Work<T> work, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure) {
            pending.add(() -> {
                T result;
                try {
                    result = work.call();
                } catch (Exception | Error e) {
                    onFailure.accept(e);
                    return;
                }
                onSuccess.accept(result);
            });
        }

        int pending() {
            return pending.size();
        }

        void runAll() {
            List<Runnable> now = new ArrayList<>(pending);
            pending.clear();
            now.forEach(Runnable::run);
        }

        @Override
        public void shutdown() {
        }
    }

    static final class ShellView extends Recorder implements ShellPresenter.View {
        String unread = "unset";

        @Override
        public void showUser(String name, String email) {
            record("user " + name + " " + email);
        }

        @Override
        public void showUnread(String text) {
            record("unread " + text);
            unread = text;
        }
    }
}
