package com.logiopt.desktop.view;

import com.logiopt.app.ShipmentFormInput;
import com.logiopt.desktop.presenter.Models.PreferenceOption;
import com.logiopt.desktop.presenter.Models.ShipmentFormModel;
import com.logiopt.desktop.presenter.ShipmentFormPresenter;
import com.logiopt.desktop.support.Formats;
import com.logiopt.model.CargoType;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * New and edit shipment form in a two-column grid: city ComboBoxes, cargo type, weight with its
 * limits, optional dimensions, a DatePicker (ISO dates) and the optimisation preference tiles.
 * Errors appear under each field. New: "Save as draft" and "Save and compare routes"; Edit adds
 * Cancel.
 */
public final class ShipmentFormView implements ShipmentFormPresenter.View {

    /** Widest the form card grows. */
    static final double MAX_WIDTH = 960;
    /** Narrowest a preference tile gets before the tiles wrap to two per row. */
    static final double PREFERENCE_TILE_MIN_WIDTH = 190;

    private final VBox root = new VBox(Theme.space(3));
    private final ComboBox<String> source = new ComboBox<>();
    private final ComboBox<String> destination = new ComboBox<>();
    private final ComboBox<String> cargoType = new ComboBox<>();
    private final TextField weight = new TextField();
    private final TextField dimensions = new TextField();
    private final DatePicker dispatchDate = new DatePicker();
    private final Map<String, Label> errors = new HashMap<>();
    private final Label general = Theme.banner(Theme.Banner.ERROR);
    private final Label heading = Theme.cardTitle("");
    private final Label intro = Theme.muted("");
    private final Label weightHint = Theme.muted("");
    private final Button save = Theme.secondaryButton("Save as draft");
    private final Button compare = Theme.primaryButton("Save and compare routes");
    private final Button cancel = Theme.secondaryButton("Cancel");
    private final ToggleGroup preferenceGroup = new ToggleGroup();
    private final CardGrid preferenceTiles = new CardGrid(PREFERENCE_TILE_MIN_WIDTH, Theme.space(1) + 4);
    private final Label preferenceError = Theme.fieldError();
    private final VBox card = Theme.card();
    private String saveLabel = "Save as draft";
    private Runnable onCancel = () -> { };

    /** Builds the form (empty until {@link #showForm}). */
    public ShipmentFormView() {
        root.setId("shipmentForm");
        root.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(MAX_WIDTH);
        card.setSpacing(Theme.space(3));
        source.setId("source");
        destination.setId("destination");
        cargoType.setId("cargoType");
        weight.setId("weightKg");
        dimensions.setId("dimensions");
        dispatchDate.setId("dispatchDate");
        save.setId("save");
        compare.setId("saveAndCompare");
        cancel.setId("cancel");
        preferenceTiles.setId("preferences");
        preferenceError.setId("preferenceError");
        general.setId("generalError");
        source.setPromptText("Choose a city");
        destination.setPromptText("Choose a city");
        cargoType.setPromptText("Choose a cargo type");
        weight.setPromptText("e.g. 5000");
        dimensions.setPromptText("e.g. 2 x 1 x 1 m");
        dispatchDate.setPromptText("yyyy-MM-dd");
        dispatchDate.setConverter(new IsoDateConverter());
        dispatchDate.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        cargoType.setConverter(new StringConverter<>() {
            @Override
            public String toString(String name) {
                if (name == null || name.isEmpty()) {
                    return "";
                }
                try {
                    return Formats.label(CargoType.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return name;
                }
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        });
        for (Control c : List.of(source, destination, cargoType, weight, dimensions, dispatchDate)) {
            Theme.styleInput(c);
        }
        compare.setDefaultButton(true);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> onCancel.run());
        weightHint.setText(ShipmentFormPresenter.WEIGHT_HINT);
        GridPane grid = new GridPane();
        grid.setHgap(Theme.space(3));
        grid.setVgap(Theme.space(3));
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
        grid.add(field("Source city", source, ShipmentFormInput.SOURCE, null), 0, 0);
        grid.add(field("Destination city", destination, ShipmentFormInput.DESTINATION, null), 1, 0);
        grid.add(field("Cargo type", cargoType, ShipmentFormInput.CARGO_TYPE, null), 0, 1);
        grid.add(field("Weight (kg)", weight, ShipmentFormInput.WEIGHT_KG, weightHint), 1, 1);
        grid.add(field("Dimensions (optional)", dimensions, ShipmentFormInput.DIMENSIONS, null), 0, 2);
        grid.add(field("Dispatch date", dispatchDate, ShipmentFormInput.DISPATCH_DATE, null), 1, 2);
        errors.put(ShipmentFormInput.PREFERENCE, preferenceError);
        VBox preference = new VBox(Theme.space(1), Theme.fieldLabel("Optimisation preference"),
                Theme.muted("Which strategy's winner is marked \"Your choice\" and pre-selected when booking. "
                        + "All four strategies are always compared."), preferenceTiles, preferenceError);
        HBox buttons = new HBox(Theme.space(2), Theme.spacer(), cancel, save, compare);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox head = new VBox(Theme.space(1) / 2, heading, intro);
        card.getChildren().addAll(head, general, grid, preference, Theme.divider(), buttons);
    }

    private VBox field(String label, Node input, String name, Label hint) {
        Label error = Theme.fieldError();
        error.setId(name + "Error");
        errors.put(name, error);
        VBox group = new VBox(Theme.space(1) - 2, Theme.fieldLabel(label), input);
        if (hint != null) {
            group.getChildren().add(hint);
        }
        group.getChildren().add(error);
        group.setMaxWidth(Double.MAX_VALUE);
        return group;
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action receives the entered values by field name when Save is chosen */
    public void onSubmit(Consumer<Map<String, String>> action) {
        save.setOnAction(e -> action.accept(values()));
    }

    /** @param action receives the entered values when "Save and compare routes" is chosen */
    public void onSubmitAndCompare(Consumer<Map<String, String>> action) {
        compare.setOnAction(e -> action.accept(values()));
    }

    /** @param action leaves the form */
    public void onCancel(Runnable action) {
        onCancel = action;
    }

    /** @return the entered values, raw, by field name */
    Map<String, String> values() {
        Map<String, String> values = new HashMap<>();
        values.put(ShipmentFormInput.SOURCE, source.getValue() == null ? "" : source.getValue());
        values.put(ShipmentFormInput.DESTINATION, destination.getValue() == null ? "" : destination.getValue());
        values.put(ShipmentFormInput.CARGO_TYPE, cargoType.getValue() == null ? "" : cargoType.getValue());
        values.put(ShipmentFormInput.WEIGHT_KG, weight.getText());
        values.put(ShipmentFormInput.DIMENSIONS, dimensions.getText());
        values.put(ShipmentFormInput.DISPATCH_DATE, dispatchDate.getEditor().getText());
        Toggle chosen = preferenceGroup.getSelectedToggle();
        values.put(ShipmentFormInput.PREFERENCE, chosen == null ? "" : String.valueOf(chosen.getUserData()));
        return values;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Loading the form…"));
    }

    @Override
    public void showForm(ShipmentFormModel model) {
        heading.setText(model.heading());
        intro.setText(model.intro());
        saveLabel = model.saveLabel();
        save.setText(saveLabel);
        boolean editing = model.editId() != null;
        cancel.setVisible(editing);
        cancel.setManaged(editing);
        String selected = model.values().getOrDefault(ShipmentFormInput.PREFERENCE, "BALANCED");
        preferenceTiles.getChildren().clear();
        preferenceGroup.getToggles().clear();
        for (PreferenceOption option : model.preferences()) {
            ToggleButton tile = Theme.choiceTile(option.label(), option.description());
            tile.setId("preference-" + option.key());
            tile.setUserData(option.key());
            tile.setToggleGroup(preferenceGroup);
            tile.setSelected(option.key().equals(selected));
            preferenceTiles.getChildren().add(tile);
        }
        preferenceGroup.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null && was != null) {
                was.setSelected(true);
            }
        });
        weightHint.setText(model.weightHint());
        source.setItems(FXCollections.observableArrayList(model.cities()));
        destination.setItems(FXCollections.observableArrayList(model.cities()));
        cargoType.setItems(FXCollections.observableArrayList(model.cargoTypes()));
        Map<String, String> v = model.values();
        source.setValue(blankToNull(v.get(ShipmentFormInput.SOURCE)));
        destination.setValue(blankToNull(v.get(ShipmentFormInput.DESTINATION)));
        cargoType.setValue(blankToNull(v.get(ShipmentFormInput.CARGO_TYPE)));
        weight.setText(v.getOrDefault(ShipmentFormInput.WEIGHT_KG, ""));
        dimensions.setText(v.getOrDefault(ShipmentFormInput.DIMENSIONS, ""));
        String date = v.getOrDefault(ShipmentFormInput.DISPATCH_DATE, "");
        dispatchDate.setValue(parse(date));
        dispatchDate.getEditor().setText(date);
        showFieldErrors(Map.of());
        root.getChildren().setAll(card);
    }

    @Override
    public void showFieldErrors(Map<String, List<String>> fieldErrors) {
        for (Map.Entry<String, Label> e : errors.entrySet()) {
            List<String> list = fieldErrors.get(e.getKey());
            Theme.setMessage(e.getValue(), list == null ? null : String.join(" ", list));
        }
        List<String> rest = fieldErrors.get(ShipmentFormInput.GENERAL);
        Theme.setMessage(general, rest == null ? null : String.join(" ", rest));
        if (!fieldErrors.isEmpty() && rest == null) {
            Theme.setMessage(general, "Please correct the highlighted fields.");
        }
    }

    @Override
    public void setBusy(boolean busy) {
        save.setDisable(busy);
        compare.setDisable(busy);
        cancel.setDisable(busy);
        save.setText(busy ? "Saving…" : saveLabel);
    }

    @Override
    public void showUnavailable(String message) {
        root.getChildren().setAll(PageStates.error(message, "Back to my shipments", onCancel));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static LocalDate parse(String text) {
        try {
            return text == null || text.isBlank() ? null : LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Shows and reads dates as yyyy-MM-dd; text that is not a date leaves the value empty. */
    private static final class IsoDateConverter extends StringConverter<LocalDate> {
        @Override
        public String toString(LocalDate date) {
            return date == null ? "" : date.toString();
        }

        @Override
        public LocalDate fromString(String text) {
            return parse(text);
        }
    }
}
