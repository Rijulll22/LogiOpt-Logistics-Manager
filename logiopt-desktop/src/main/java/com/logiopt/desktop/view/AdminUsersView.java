package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.AdminUsersPresenter;
import com.logiopt.desktop.presenter.Models.UserRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.List;

/** Every user account for the administrator: name, email, role and state. No password data. */
public final class AdminUsersView implements AdminUsersPresenter.View {

    private final VBox root = new VBox(Theme.space(3));
    private final TableView<UserRow> table = Tables.table("No users.");
    private final VBox card = Theme.card();
    private Runnable retry = () -> { };

    /** Builds the page. */
    public AdminUsersView() {
        root.setId("adminUsers");
        table.setId("usersTable");
        table.getColumns().add(Tables.text("User", r -> "#" + r.id(), 0.5, 56));
        table.getColumns().add(Tables.text("Name", UserRow::name, 1.6, 120));
        table.getColumns().add(Tables.text("Email", UserRow::email, 2.0, 150));
        table.getColumns().add(Tables.chip("Role", UserRow::role, UserRow::roleChip, 0.9, 90));
        table.getColumns().add(Tables.chip("State", UserRow::state, UserRow::stateChip, 0.9, 90));
        card.getChildren().addAll(new VBox(2, Theme.cardTitle("Users"),
                Theme.muted("Every account. Passwords are stored only as salted hashes and are never shown.")), table);
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Loading the users…"));
    }

    @Override
    public void showUsers(List<UserRow> rows) {
        Tables.show(table, rows);
        root.getChildren().setAll(card);
    }

    @Override
    public void showError(String message) {
        root.getChildren().setAll(PageStates.error(message, "Try again", retry));
    }
}
