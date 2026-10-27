package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.NotificationsModel;
import com.logiopt.desktop.presenter.Models.ReportJobRow;
import com.logiopt.desktop.presenter.Models.ReportProgress;
import com.logiopt.desktop.presenter.Models.UserRow;
import com.logiopt.desktop.support.ChipKind;
import com.logiopt.model.Notification;
import com.logiopt.model.NotificationType;
import com.logiopt.model.Role;
import com.logiopt.model.User;
import com.logiopt.report.ReportRecord;
import com.logiopt.report.ReportStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Notifications, report status and user rows built for the D4 screens; no display, no database. */
class D4ViewModelsTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 28, 14, 30);

    private static ReportRecord report(ReportStatus status, String error) {
        return new ReportRecord("7c0d92e1-0000-0000-0000-000000000000", 3, 18, "GREENEST", status, AT, AT,
                status == ReportStatus.DONE || status == ReportStatus.FAILED ? AT : null,
                status == ReportStatus.DONE ? "report-7c0d92e1.txt" : null, error);
    }

    @Test
    void aFailedReportShowsOnlyTheGenericMessage() {
        ReportProgress p = ViewModels.progress(report(ReportStatus.FAILED,
                "java.nio.file.AccessDeniedException: D:\\secret\\reports at Foo.java:12"));
        assertEquals(ViewModels.REPORT_FAILED, p.message());
        assertTrue(p.finished());
        assertFalse(p.done());
        assertEquals(ChipKind.DANGER, p.chip());
        assertFalse(ViewModels.reportJobRow(report(ReportStatus.FAILED, "x")).canSave());
    }

    @Test
    void reportStatesAndTheSuggestedFileName() {
        assertFalse(ViewModels.progress(report(ReportStatus.QUEUED, null)).finished());
        assertEquals("Writing the report…", ViewModels.progress(report(ReportStatus.RUNNING, null)).message());
        ReportProgress done = ViewModels.progress(report(ReportStatus.DONE, null));
        assertTrue(done.done());
        ReportJobRow row = ViewModels.reportJobRow(report(ReportStatus.DONE, null));
        assertTrue(row.canSave());
        assertEquals("7c0d92e1", row.job());
        assertEquals("logiopt-report-shipment-18-greenest.txt", ReportsPresenter.suggestedName(row));
    }

    @Test
    void notificationsCountUnreadAndKeepTheirOrder() {
        NotificationsModel m = ViewModels.notifications(List.of(
                new Notification(2, 3, NotificationType.TRACKING_UPDATE, 12, "Booking 12: Picked up.", false, AT, null),
                new Notification(1, 3, NotificationType.BOOKING_CREATED, 12, "Booking 12 created.", true, AT, AT)));
        assertEquals(1, m.unread());
        assertEquals("1 unread of 2", m.summary());
        assertTrue(m.rows().get(0).unread());
        assertEquals(12, m.rows().get(0).bookingId());
        assertEquals("All caught up · 1 notification", ViewModels.notifications(List.of(m.rows().isEmpty() ? null
                : new Notification(1, 3, NotificationType.BOOKING_CREATED, 12, "x", true, AT, AT))).summary());
        assertEquals(0, ViewModels.notifications(List.of()).unread());
    }

    @Test
    void userRowsShowRoleAndState() {
        UserRow admin = ViewModels.userRow(new User(1, "admin@logiopt.local", "Admin", Role.ADMIN, true));
        assertEquals(ChipKind.INFO, admin.roleChip());
        assertEquals("Enabled", admin.state());
        assertEquals("Disabled", ViewModels.userRow(new User(2, "d@x.test", "Demo", Role.CUSTOMER, false)).state());
    }
}
