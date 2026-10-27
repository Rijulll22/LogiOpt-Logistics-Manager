package com.logiopt.desktop.tools;

import com.logiopt.desktop.presenter.LoginPresenter;
import com.logiopt.desktop.support.Screen;
import com.logiopt.desktop.view.OrderSummaryView;
import com.logiopt.desktop.view.PaymentView;
import com.logiopt.desktop.view.Theme;
import com.logiopt.model.OptimisationPreference;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Builds every desktop screen with sample data in off-screen scenes at every window size of the
 * gallery (typical screens, this machine's screen and the minimum window), lays them out and checks
 * the layout rules. Needs the JavaFX toolkit, which needs a display: without one every test here is
 * SKIPPED (not passed), and the build report shows the skip.
 */
class ViewSmokeTest {

    private static List<ScreenGallery.Size> sizes;

    @BeforeAll
    static void toolkit() throws Exception {
        boolean started = FxToolkit.start(Duration.ofSeconds(15));
        assumeTrue(started, "No display: the JavaFX toolkit could not start (" + FxToolkit.failure()
                + "); view smoke tests skipped");
        sizes = FxToolkit.call(ScreenGallery::sizes);
    }

    private static Parent laidOut(Parent root, ScreenGallery.Size size) {
        new Scene(root, size.width(), size.height());
        for (int i = 0; i < 3; i++) {
            root.applyCss();
            root.layout();
        }
        return root;
    }

    private static Bounds inScene(Parent root, String id) {
        Node node = root.lookup(id);
        assertNotNull(node, id);
        return node.localToScene(node.getBoundsInLocal());
    }

    /** @return true if the node and all its parents are visible */
    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static ScreenGallery.Size minimum() {
        return sizes.get(sizes.size() - 1);
    }

    @Test
    void sizesFollowTheWindowSizingRules() {
        assertEquals(5, sizes.size());
        for (ScreenGallery.Size size : sizes) {
            assertTrue(size.width() >= 960 - 0.5 && size.height() >= 568 - 0.5, size.toString());
        }
        assertEquals("minimum-window", minimum().name());
        assertEquals(1229, sizes.get(0).width(), 0.5, "90% of a 1366 px wide screen");
    }

    @Test
    void everyGalleryScreenRendersAtItsSize() throws Exception {
        List<ScreenGallery.Shot> shots = FxToolkit.call(ScreenGallery::shots);
        assertTrue(shots.size() >= sizes.size() * 25 + 2, "every page at every size: " + shots.size());
        for (ScreenGallery.Shot shot : shots) {
            WritableImage image = FxToolkit.call(() -> ScreenshotTool.render(shot, 1));
            assertEquals(Math.round(shot.width()), Math.round(image.getWidth()), shot.name());
            assertEquals(Math.round(shot.height()), Math.round(image.getHeight()), shot.name());
        }
    }

    @Test
    void signInCardHasItsFieldsAndShowsErrors() throws Exception {
        FxToolkit.call(() -> {
            Parent root = laidOut(ScreenGallery.login(null, LoginPresenter.INVALID_MESSAGE), minimum());
            assertNotNull(root.lookup("#email"));
            assertNotNull(root.lookup("#password"));
            assertTrue(((Button) root.lookup("#signIn")).isDefaultButton());
            Label error = (Label) root.lookup("#error");
            assertTrue(error.isVisible());
            assertEquals(LoginPresenter.INVALID_MESSAGE, error.getText());
            assertFalse(root.lookup("#notice").isVisible(), "an empty notice takes no space");
            return null;
        });
    }

    @Test
    void topBarShowsTitleUserBadgeAndSignOutWithoutClippingAtEverySize() throws Exception {
        FxToolkit.call(() -> {
            for (ScreenGallery.Size size : sizes) {
                for (Supplier<Parent> page : List.<Supplier<Parent>>of(
                        () -> ScreenGallery.dashboard(SampleData.customerDashboard(), false, "3 unread"),
                        () -> ScreenGallery.form(SampleData.editForm(), Map.of(), Screen.EDIT_SHIPMENT),
                        ScreenGallery::history)) {
                    Parent root = laidOut(page.get(), size);
                    Bounds title = inScene(root, "#pageTitle");
                    Bounds badge = inScene(root, "#unreadBadge");
                    Bounds name = inScene(root, "#userName");
                    Bounds email = inScene(root, "#userEmail");
                    Bounds signOut = inScene(root, "#signOut");
                    Label titleLabel = (Label) root.lookup("#pageTitle");
                    assertTrue(titleLabel.getWidth() >= titleLabel.prefWidth(-1) - 0.5, size + " title clipped");
                    assertTrue(title.getMaxX() <= badge.getMinX(), size + " title overlaps the badge");
                    assertTrue(badge.getMaxX() <= Math.min(name.getMinX(), email.getMinX()), size + " badge overlaps the user");
                    assertTrue(Math.max(name.getMaxX(), email.getMaxX()) <= signOut.getMinX(), size + " user overlaps Sign out");
                    assertTrue(signOut.getMaxX() <= size.width(), size + " Sign out cut off");
                    assertTrue(title.getMinX() >= Theme.SIDEBAR_WIDTH, size + " title under the sidebar");
                }
            }
            return null;
        });
    }

    @Test
    void contentIsACentredColumnAtMost1100WideWithoutHorizontalScrolling() throws Exception {
        FxToolkit.call(() -> {
            for (ScreenGallery.Size size : sizes) {
                for (Supplier<Parent> page : List.<Supplier<Parent>>of(
                        () -> ScreenGallery.shipments(SampleData.shipments(), null), ScreenGallery::history,
                        () -> ScreenGallery.account(SampleData.account(), false, true),
                        () -> ScreenGallery.dashboard(SampleData.customerDashboard(), false, "3 unread"),
                        () -> ScreenGallery.comparison(SampleData.comparison(OptimisationPreference.BALANCED,
                                SampleData.Variant.EXCLUDED_ROUTE)),
                        ScreenGallery::bookingDetail, () -> ScreenGallery.bookings(SampleData.bookings()))) {
                    Parent root = laidOut(page.get(), size);
                    Node columnNode = root.lookup("#contentColumn");
                    Bounds column = columnNode.localToScene(columnNode.getLayoutBounds());
                    assertTrue(column.getWidth() <= Theme.CONTENT_MAX_WIDTH + 0.5, size + " column too wide");
                    ScrollPane scroll = (ScrollPane) root.lookup("#pageScroll");
                    Bounds viewport = scroll.localToScene(scroll.getLayoutBounds());
                    double viewportRight = viewport.getMinX() + scroll.getViewportBounds().getWidth();
                    assertTrue(column.getMaxX() <= viewportRight + 0.5, size + " column beyond the window");
                    double left = column.getMinX() - viewport.getMinX();
                    double right = viewportRight - column.getMaxX();
                    assertEquals(left, right, 1.5, size + " column not centred");
                    assertTrue(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 0.5,
                            size + " page wider than the window");
                    for (Node node : root.lookupAll(".table-view")) {
                        TableView<?> table = (TableView<?>) node;
                        double columns = table.getColumns().stream().mapToDouble(c -> c.getWidth()).sum();
                        assertTrue(columns <= table.getWidth(), size + " table scrolls horizontally: " + table.getId());
                    }
                }
            }
            return null;
        });
    }

    @Test
    void tablesAreAsTallAsTheirRowsAndShowAnEmptyState() throws Exception {
        FxToolkit.call(() -> {
            Parent root = laidOut(ScreenGallery.shipments(SampleData.shipments(), null), sizes.get(1));
            TableView<?> table = (TableView<?>) root.lookup("#shipmentsTable");
            assertEquals(5, table.getItems().size());
            assertEquals(Theme.TABLE_HEADER_HEIGHT + 5 * Theme.ROW_HEIGHT + 2, table.getHeight(), 0.5, "no filler rows");
            assertNotNull(root.lookup("#edit-21"), "drafts have an Edit action");
            assertNull(root.lookup("#edit-19"), "booked shipments do not");
            Parent empty = laidOut(ScreenGallery.shipments(List.of(), null), sizes.get(1));
            TableView<?> none = (TableView<?>) empty.lookup("#shipmentsTable");
            assertEquals(Theme.TABLE_EMPTY_HEIGHT, none.getHeight(), 0.5);
            assertTrue(((Label) none.getPlaceholder()).getText().startsWith("No shipments yet"));
            Parent history = laidOut(ScreenGallery.history(), sizes.get(1));
            assertNotNull(history.lookup("#historyShipments"));
            ((ButtonBase) history.lookup("#tab-1")).fire();
            history.layout();
            assertNotNull(history.lookup("#historyBookings"));
            return null;
        });
    }

    @Test
    void shipmentFormKeepsTwoColumnsAndInlineErrorsAtTheMinimumSize() throws Exception {
        FxToolkit.call(() -> {
            Parent root = laidOut(ScreenGallery.form(SampleData.invalidForm(), SampleData.formErrors(), Screen.NEW_SHIPMENT),
                    minimum());
            ComboBox<?> source = (ComboBox<?>) root.lookup("#source");
            assertEquals(6, source.getItems().size());
            assertTrue(root.lookup("#destinationError").isVisible());
            assertTrue(root.lookup("#weightKgError").isVisible());
            assertTrue(root.lookup("#dispatchDateError").isVisible());
            assertFalse(root.lookup("#sourceError").isVisible());
            Bounds destination = inScene(root, "#destination");
            assertTrue(destination.getMinX() > inScene(root, "#source").getMaxX(), "two columns side by side");
            assertTrue(destination.getMaxX() <= minimum().width(), "nothing is cut off at the minimum width");
            return null;
        });
    }

    @Test
    void comparisonShowsTheRecommendationFourStrategiesTheTableAndThreeCharts() throws Exception {
        FxToolkit.call(() -> {
            for (ScreenGallery.Size size : sizes) {
                Parent root = laidOut(ScreenGallery.comparison(SampleData.comparison(OptimisationPreference.BALANCED,
                        SampleData.Variant.STANDARD)), size);
                assertTrue(((Label) root.lookup("#recommendedScore")).getText().contains("0.167971"), size.toString());
                assertEquals(4, ((Parent) root.lookup("#strategyCards")).getChildrenUnmodifiable().size());
                assertEquals(4, ((TableView<?>) root.lookup("#comparisonTable")).getItems().size());
                assertEquals(3, ((Parent) root.lookup("#charts")).getChildrenUnmodifiable().size());
                assertNotNull(root.lookup("#chart-total-cost"));
                assertFalse(((Button) root.lookup("#book-2")).isDisabled());
                assertNull(root.lookup("#bookBlocked"));
                assertTrue(root.lookupAll(".label").stream().anyMatch(n -> "Your choice".equals(((Label) n).getText())));
            }
            Parent past = laidOut(ScreenGallery.comparison(SampleData.comparison(OptimisationPreference.FASTEST,
                    SampleData.Variant.PAST_DATED)), minimum());
            assertTrue(shown(past.lookup("#bookBlocked")));
            assertTrue(((Button) past.lookup("#book-1")).isDisabled(), "Book is disabled for a past dispatch date");
            assertTrue(((Button) past.lookup("#bookRecommended")).isDisabled());
            Parent none = laidOut(ScreenGallery.comparison(SampleData.comparison(OptimisationPreference.GREENEST,
                    SampleData.Variant.NO_GREENEST)), minimum());
            assertTrue(shown(none.lookup("#noRecommendation")));
            assertFalse(((Button) none.lookup("#book-1")).isDisabled(), "any eligible route can still be booked");
            Parent excluded = laidOut(ScreenGallery.comparison(SampleData.comparison(OptimisationPreference.CHEAPEST,
                    SampleData.Variant.EXCLUDED_ROUTE)), minimum());
            assertTrue(shown(excluded.lookup("#excludedCard")));
            return null;
        });
    }

    @Test
    void orderSummaryPaymentAndBookingViewsHaveTheirParts() throws Exception {
        FxToolkit.call(() -> {
            Parent order = new OrderSummaryView(SampleData.orderSummary()).root();
            laidOut(order, new ScreenGallery.Size("dialog", 640, 520));
            assertNotNull(order.lookup("#confirmBooking"));
            assertNotNull(order.lookup("#cancelOrder"));
            assertNotNull(order.lookup("#summaryLegs"));
            assertEquals("INR 22,800.00", ((Label) order.lookup("#summaryTotal")).getText());
            assertTrue(String.valueOf(((ComboBox<?>) order.lookup("#orderStrategy")).getValue()).contains("BALANCED"),
                    "the preferred strategy is pre-selected");

            PaymentView paymentView = new PaymentView(12, "INR 22,800.00");
            Parent payment = paymentView.root();
            laidOut(payment, new ScreenGallery.Size("dialog", 520, 470));
            assertEquals(PaymentView.SIMULATION, ((Label) payment.lookup("#simulationBanner")).getText());
            assertEquals(3, ((ComboBox<?>) payment.lookup("#paymentMethod")).getItems().size());
            assertTrue(payment.lookupAll(".text-field").isEmpty(), "no card number or other text fields");
            paymentView.showResult(false, "failed");
            assertFalse(((Button) payment.lookup("#simulateSuccess")).isDisabled(), "retry after a failure");
            paymentView.showResult(true, "paid");
            assertTrue(((Button) payment.lookup("#simulateSuccess")).isDisabled());

            for (ScreenGallery.Size size : sizes) {
                Parent detail = laidOut(ScreenGallery.bookingDetail(), size);
                assertNotNull(detail.lookup("#payNow"), size.toString());
                assertNotNull(detail.lookup("#cancelBooking"));
                assertEquals(1, ((TableView<?>) detail.lookup("#paymentsTable")).getItems().size());
                for (Node chip : ((Parent) detail.lookup("#bookingChips")).getChildrenUnmodifiable()) {
                    if (chip instanceof Label && ((Label) chip).getBackground() != null
                            && !((Label) chip).getText().equals("Booking") && !((Label) chip).getText().equals("Payment")) {
                        assertNotNull(((Label) chip).getTooltip(), "chips explain their status");
                    }
                }
                Parent list = laidOut(ScreenGallery.bookings(SampleData.bookings()), size);
                assertEquals(3, ((TableView<?>) list.lookup("#bookingsTable")).getItems().size());
                assertNotNull(list.lookup("#open-15"));
                Parent shipments = laidOut(ScreenGallery.shipments(SampleData.shipments(), null), size);
                assertNotNull(shipments.lookup("#compare-19"), "Compare on every row");
                assertNotNull(shipments.lookup("#statusHelp"));
            }
            return null;
        });
    }

    @Test
    void accountScreenShowsProfileFormsAndHidesThePasswordFormForTheAdministrator() throws Exception {
        FxToolkit.call(() -> {
            Parent root = laidOut(ScreenGallery.account(SampleData.account(), false, true), minimum());
            assertEquals(SampleData.NAME, ((Label) root.lookup("#profileName")).getText());
            assertEquals(SampleData.EMAIL, ((Label) root.lookup("#profileEmail")).getText());
            for (String id : List.of("#accountName", "#currentPassword", "#newPassword", "#confirmPassword", "#saveName",
                    "#changePassword", "#menu-ACCOUNT")) {
                assertTrue(shown(root.lookup(id)), id);
            }
            assertTrue(root.lookup("#currentError").isVisible());
            assertTrue(root.lookup("#newError").isVisible());
            Parent admin = laidOut(ScreenGallery.account(SampleData.adminAccount(), true, false), minimum());
            assertFalse(shown(admin.lookup("#currentPassword")), "no password form for the administrator");
            assertNull(admin.lookup("#unreadBadge"), "no badge for the administrator");
            return null;
        });
    }
    @Test
    void d4ScreensRenderAtBothSizesAsACentredColumnWithoutHorizontalScrolling() throws Exception {
        List<ScreenGallery.Shot> shots = FxToolkit.call(ScreenGallery::d4Shots);
        assertTrue(shots.size() >= 2 * 18, "every D4 page at both sizes: " + shots.size());
        FxToolkit.call(() -> {
            for (ScreenGallery.Shot shot : shots) {
                Parent root = laidOut(shot.build().get(), new ScreenGallery.Size(shot.name(), shot.width(), shot.height()));
                Node columnNode = root.lookup("#contentColumn");
                Bounds column = columnNode.localToScene(columnNode.getLayoutBounds());
                assertTrue(column.getWidth() <= Theme.CONTENT_MAX_WIDTH + 0.5, shot.name() + " column too wide");
                ScrollPane scroll = (ScrollPane) root.lookup("#pageScroll");
                assertTrue(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 0.5,
                        shot.name() + " page wider than the window");
                for (Node node : root.lookupAll(".table-view")) {
                    TableView<?> table = (TableView<?>) node;
                    double columns = table.getColumns().stream().mapToDouble(c -> c.getWidth()).sum();
                    assertTrue(columns <= table.getWidth(), shot.name() + " table scrolls horizontally: " + table.getId());
                    if (!table.getItems().isEmpty()) {
                        assertEquals(Theme.TABLE_HEADER_HEIGHT + table.getItems().size() * Theme.ROW_HEIGHT + 2,
                                table.getHeight(), 0.5, shot.name() + " table not as tall as its rows: " + table.getId());
                    }
                }
            }
            return null;
        });
    }

    @Test
    void d4ViewsShowTimelineNotificationsReportsAndAdminParts() throws Exception {
        ScreenGallery.Size size = minimum();
        FxToolkit.call(() -> {
            Parent transfer = laidOut(ScreenGallery.booking(SampleData.confirmedBooking(SampleData.timeline(4, true)),
                    false), size);
            assertEquals(6, ((Parent) transfer.lookup("#timeline")).getChildrenUnmodifiable().size());
            assertNotNull(transfer.lookup("#step-At-transfer"));
            Parent single = laidOut(ScreenGallery.booking(SampleData.confirmedBooking(SampleData.timeline(3, false)),
                    false), size);
            assertEquals(5, ((Parent) single.lookup("#timeline")).getChildrenUnmodifiable().size());
            assertNull(single.lookup("#step-At-transfer"), "no transfer on a single-leg route");
            Parent unpaid = laidOut(ScreenGallery.booking(SampleData.bookingDetail(), false), size);
            assertNull(unpaid.lookup("#timeline"));
            assertTrue(shown(unpaid.lookup("#timelineUnavailable")));

            Parent notes = laidOut(ScreenGallery.notifications(SampleData.notifications(false), "2 unread"), size);
            assertEquals(4, ((TableView<?>) notes.lookup("#notificationsTable")).getItems().size());
            assertNotNull(notes.lookup("#markRead-41"));
            assertNull(notes.lookup("#markRead-39"), "read notifications have no Mark as read");
            assertNotNull(notes.lookup("#openNotification-39"));
            Parent noNotes = laidOut(ScreenGallery.notifications(SampleData.notifications(true), "No unread"), size);
            assertTrue(((ButtonBase) noNotes.lookup("#markAllRead")).isDisabled());

            Parent running = laidOut(ScreenGallery.reports(SampleData.reports(false),
                    SampleData.progress(com.logiopt.report.ReportStatus.RUNNING), true), size);
            assertTrue(((ButtonBase) running.lookup("#generateReport")).isDisabled(), "disabled while running");
            assertTrue(shown(running.lookup("#reportProgress")));
            assertNotNull(running.lookup("#save-32f1114b"), "a done report can be saved");
            assertNull(running.lookup("#save-7c0d92e1"), "a failed one cannot");
            Parent failed = laidOut(ScreenGallery.reports(SampleData.reports(false),
                    SampleData.progress(com.logiopt.report.ReportStatus.FAILED), false), size);
            assertEquals(com.logiopt.desktop.presenter.ViewModels.REPORT_FAILED,
                    ((Label) failed.lookup("#reportProgressMessage")).getText());
            Parent empty = laidOut(ScreenGallery.reports(SampleData.reports(true), null, false), size);
            assertTrue(((ButtonBase) empty.lookup("#generateReport")).isDisabled(), "nothing to report on");

            Parent tracking = laidOut(ScreenGallery.adminTracking(false), size);
            assertNotNull(tracking.lookup("#record-12"));
            assertNotNull(tracking.lookup("#advance-16"));
            assertNotNull(tracking.lookup("#description-12"));
            assertNotNull(tracking.lookup("#menu-ADMIN_TRACKING"));
            assertNull(tracking.lookup("#menu-SHIPMENTS"), "the administrator's menu has no customer pages");
            assertNull(tracking.lookup("#unreadBadge"));
            Parent customer = laidOut(ScreenGallery.notifications(SampleData.notifications(false), "2 unread"), size);
            assertNull(customer.lookup("#menu-ADMIN_USERS"), "customers see no administrator pages");
            Parent users = laidOut(ScreenGallery.adminUsers(), size);
            assertEquals(4, ((TableView<?>) users.lookup("#usersTable")).getItems().size());
            return null;
        });
    }
}
