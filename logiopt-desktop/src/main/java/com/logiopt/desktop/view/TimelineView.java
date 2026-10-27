package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.Models.StepState;
import com.logiopt.desktop.presenter.Models.TimelineModel;
import com.logiopt.desktop.presenter.Models.TimelineStep;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

/**
 * The tracking card of the booking page: a vertical timeline of the booked route's milestones. Done
 * steps show their time, source and description, the current step is highlighted, the next step is
 * marked pending; an unpaid or cancelled booking shows why there is no timeline instead.
 */
final class TimelineView {

    private TimelineView() {
    }

    /** @return the tracking card */
    static VBox of(TimelineModel model) {
        VBox card = Theme.card();
        card.setId("timelineCard");
        Label summary = Theme.muted(model.message());
        summary.setId("timelineMessage");
        card.getChildren().add(new VBox(2, Theme.cardTitle("Tracking"), summary));
        if (!model.tracked()) {
            Label notice = Theme.banner(Theme.Banner.NOTICE);
            notice.setId("timelineUnavailable");
            Theme.setMessage(notice, model.message());
            card.getChildren().setAll(Theme.cardTitle("Tracking"), notice);
            return card;
        }
        VBox steps = new VBox();
        steps.setId("timeline");
        for (int i = 0; i < model.steps().size(); i++) {
            TimelineStep step = model.steps().get(i);
            boolean last = i == model.steps().size() - 1;
            boolean nextDone = !last && isRecorded(model.steps().get(i + 1).state());
            steps.getChildren().add(row(step, last, nextDone));
        }
        card.getChildren().add(steps);
        return card;
    }

    private static boolean isRecorded(StepState state) {
        return state == StepState.DONE || state == StepState.CURRENT;
    }

    private static HBox row(TimelineStep step, boolean last, boolean nextDone) {
        VBox rail = new VBox(Theme.timelineMarker(step.state()));
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setMinWidth(Theme.MARKER + 8);
        rail.setPadding(new Insets(Theme.space(1) + 2, 0, 0, 0));
        if (!last) {
            Region line = Theme.timelineConnector(nextDone);
            VBox.setVgrow(line, Priority.ALWAYS);
            line.setMaxHeight(Double.MAX_VALUE);
            rail.getChildren().add(line);
        }
        boolean recorded = isRecorded(step.state());
        Label title = Theme.text(step.label(), 14, FontWeight.BOLD, recorded || step.state() == StepState.NEXT
                ? Theme.TEXT : Theme.MUTED);
        HBox head = new HBox(Theme.space(1), title);
        head.setAlignment(Pos.CENTER_LEFT);
        if (step.state() == StepState.CURRENT) {
            head.getChildren().add(Theme.chip("Current", com.logiopt.desktop.support.ChipKind.SUCCESS));
        } else if (step.state() == StepState.NEXT) {
            head.getChildren().add(Theme.chip("Pending", com.logiopt.desktop.support.ChipKind.WARNING));
        }
        VBox body = new VBox(2, head);
        if (recorded) {
            body.getChildren().add(Theme.muted(step.time() + " · " + step.source()));
        }
        if (!step.description().isEmpty()) {
            Label text = Theme.text(step.description(), 13, FontWeight.NORMAL, recorded ? Theme.TEXT : Theme.MUTED);
            body.getChildren().add(text);
        }
        body.setPadding(new Insets(Theme.space(1), Theme.space(2), Theme.space(1), Theme.space(2)));
        body.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(body, Priority.ALWAYS);
        if (step.state() == StepState.CURRENT) {
            Theme.currentStep(body);
        }
        HBox row = new HBox(Theme.space(1), rail, body);
        row.setId("step-" + step.label().replace(' ', '-'));
        row.setPadding(new Insets(0, 0, last ? 0 : 2, 0));
        return row;
    }
}
