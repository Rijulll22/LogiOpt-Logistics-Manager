package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.Models.ReportJobRow;
import com.logiopt.desktop.presenter.Models.ReportProgress;
import com.logiopt.desktop.presenter.Models.ReportsModel;
import com.logiopt.desktop.presenter.Models.ShipmentChoice;
import com.logiopt.desktop.presenter.Models.StrategyOption;
import com.logiopt.desktop.presenter.ReportsPresenter;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Reports: choose a shipment and a strategy (the shipment's preference by default), Generate, follow
 * the status until Done or Failed, and the user's reports with "Save a copy…" (a native save dialog).
 */
public final class ReportsView implements ReportsPresenter.View {

    private final VBox root = new VBox(Theme.space(3));
    private final Label message = Theme.banner(Theme.Banner.NOTICE);
    private final ComboBox<ShipmentChoice> shipment = new ComboBox<>();
    private final ComboBox<StrategyOption> strategy = new ComboBox<>();
    private final Button generate = Theme.primaryButton("Generate report");
    private final HBox progress = new HBox(Theme.space(1));
    private final VBox form = Theme.card();
    private final VBox list = Theme.card();
    private final Label note = Theme.muted("");
    private final TableView<ReportJobRow> table = Tables.table("No reports yet. Generate one above; it is listed here "
            + "with its status.");
    private BiConsumer<Long, String> onGenerate = (id, key) -> { };
    private BiConsumer<String, Path> onSave = (job, path) -> { };
    private Function<ReportJobRow, String> fileName = row -> "report.txt";
    private Runnable retry = () -> { };

    /** Builds the page. */
    public ReportsView() {
        root.setId("reports");
        message.setId("reportsMessage");
        shipment.setId("reportShipment");
        strategy.setId("reportStrategy");
        generate.setId("generateReport");
        progress.setId("reportProgress");
        table.setId("reportsTable");
        Theme.styleInput(shipment);
        Theme.styleInput(strategy);
        shipment.setCellFactory(v -> cell(ShipmentChoice::label));
        shipment.setButtonCell(cell(ShipmentChoice::label));
        strategy.setCellFactory(v -> cell(StrategyOption::name));
        strategy.setButtonCell(cell(StrategyOption::name));
        shipment.valueProperty().addListener((o, was, now) -> preferStrategyOf(now));
        generate.setMinWidth(Region.USE_PREF_SIZE);
        generate.setOnAction(e -> {
            if (shipment.getValue() != null && strategy.getValue() != null) {
                onGenerate.accept(shipment.getValue().id(), strategy.getValue().key());
            }
        });
        progress.setAlignment(Pos.CENTER_LEFT);
        showProgressRow(false);
        GridPane grid = new GridPane();
        grid.setHgap(Theme.space(2));
        grid.setVgap(Theme.space(1) / 2);
        grid.add(Theme.fieldLabel("Shipment"), 0, 0);
        grid.add(Theme.fieldLabel("Strategy"), 1, 0);
        grid.add(shipment, 0, 1);
        grid.add(strategy, 1, 1);
        grid.add(generate, 2, 1);
        GridPane.setHgrow(shipment, Priority.ALWAYS);
        shipment.setMinWidth(200);
        strategy.setMinWidth(150);
        strategy.setPrefWidth(180);
        form.getChildren().addAll(new VBox(2, Theme.cardTitle("New report"), Theme.muted("A text report of the route "
                + "comparison for one of your saved shipments, also for one with a past dispatch date.")), grid,
                progress);
        table.getColumns().add(Tables.text("Report", ReportJobRow::job, 0.9, 72));
        table.getColumns().add(Tables.text("Shipment", r -> "#" + r.shipmentId(), 0.7, 64));
        table.getColumns().add(Tables.text("Strategy", ReportJobRow::strategy, 0.9, 76));
        table.getColumns().add(Tables.chip("Status", ReportJobRow::status, ReportJobRow::chip, ReportJobRow::hint, 0.9,
                80));
        table.getColumns().add(Tables.node("Created", r -> dateCell(r.created()), 1.0, 84));
        table.getColumns().add(Tables.node("Finished", r -> dateCell(r.finished()), 1.0, 84));
        table.getColumns().add(Tables.node("", this::saveButton, 1.0, 100));
        list.getChildren().addAll(new VBox(2, Theme.cardTitle("Your reports"), note), table);
    }

    private static <T> ListCell<T> cell(Function<T, String> text) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : text.apply(item));
            }
        };
    }

    /** Date over time, so the column stays narrow without wrapping mid-date. */
    private static Node dateCell(String at) {
        int space = at.indexOf(' ');
        if (space < 0) {
            return Theme.text(at, 13, javafx.scene.text.FontWeight.NORMAL, Theme.TEXT);
        }
        return new VBox(1, Theme.text(at.substring(0, space), 13, javafx.scene.text.FontWeight.NORMAL, Theme.TEXT),
                Theme.text(at.substring(space + 1), 12, javafx.scene.text.FontWeight.NORMAL, Theme.MUTED));
    }

    private void preferStrategyOf(ShipmentChoice choice) {
        if (choice == null) {
            return;
        }
        for (StrategyOption option : strategy.getItems()) {
            if (option.key().equals(choice.preferredKey())) {
                strategy.setValue(option);
            }
        }
    }

    private Node saveButton(ReportJobRow row) {
        if (!row.canSave()) {
            return new Region();
        }
        Button save = Theme.linkButton("Save a copy…");
        save.setId("save-" + row.job());
        save.setOnAction(e -> saveCopy(row));
        return save;
    }

    private void saveCopy(ReportJobRow row) {
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        if (owner == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save a copy of the report");
        chooser.setInitialFileName(fileName.apply(row));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files (*.txt)", "*.txt"));
        File target = chooser.showSaveDialog(owner);
        if (target != null) {
            onSave.accept(row.jobId(), target.toPath());
        }
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action receives the shipment id and strategy key */
    public void onGenerate(BiConsumer<Long, String> action) {
        onGenerate = action;
    }

    /** @param action receives the job id and the chosen file */
    public void onSaveCopy(BiConsumer<String, Path> action) {
        onSave = action;
    }

    /** @param name gives the file name offered in the save dialog */
    public void fileNames(Function<ReportJobRow, String> name) {
        fileName = name;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Loading your reports…"));
    }

    @Override
    public void showReports(ReportsModel model) {
        ShipmentChoice chosen = shipment.getValue();
        StrategyOption picked = strategy.getValue();
        strategy.getItems().setAll(model.strategies());
        shipment.getItems().setAll(model.shipments());
        boolean any = !model.shipments().isEmpty();
        shipment.setPromptText(any ? "Choose a shipment" : "No saved shipments yet");
        shipment.setDisable(!any);
        strategy.setDisable(!any);
        generate.setDisable(!any);
        ShipmentChoice keep = find(model.shipments(), chosen);
        shipment.setValue(keep != null ? keep : any ? model.shipments().get(0) : null);
        if (keep != null && picked != null) {
            for (StrategyOption option : strategy.getItems()) {
                if (option.key().equals(picked.key())) {
                    strategy.setValue(option);
                }
            }
        }
        note.setText(model.note());
        Tables.show(table, model.reports());
        root.getChildren().setAll(message, form, list);
    }

    private static ShipmentChoice find(List<ShipmentChoice> choices, ShipmentChoice chosen) {
        if (chosen == null) {
            return null;
        }
        for (ShipmentChoice c : choices) {
            if (c.id() == chosen.id()) {
                return c;
            }
        }
        return null;
    }

    @Override
    public void showError(String text) {
        root.getChildren().setAll(PageStates.error(text, "Try again", retry));
    }

    @Override
    public void showProgress(ReportProgress p) {
        Label status = Theme.text(p.message(), 13, javafx.scene.text.FontWeight.NORMAL, Theme.TEXT);
        status.setId("reportProgressMessage");
        progress.getChildren().setAll(Theme.fieldLabel("Report " + shortId(p.jobId())), Theme.chip(p.status(), p.chip()),
                status);
        showProgressRow(true);
    }

    private static String shortId(String jobId) {
        return jobId == null ? "" : jobId.length() > 8 ? jobId.substring(0, 8) : jobId;
    }

    private void showProgressRow(boolean show) {
        progress.setVisible(show);
        progress.setManaged(show);
    }

    @Override
    public void showMessage(String text) {
        Theme.setMessage(message, text);
    }

    @Override
    public void setBusy(boolean busy) {
        generate.setDisable(busy || shipment.getItems().isEmpty());
        generate.setText(busy ? "Generating…" : "Generate report");
        shipment.setDisable(busy || shipment.getItems().isEmpty());
        strategy.setDisable(busy || shipment.getItems().isEmpty());
    }
}
