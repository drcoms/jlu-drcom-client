package com.youthlin.jlu.drcom.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;

import static com.youthlin.utils.i18n.Translation.__;

/**
 * Created by lin on 2017-01-11-011.
 * Fx 工具类
 */
@SuppressWarnings("WeakerAccess")
public class FxUtil {
    public static final Image icon = new Image(Constants.LOGO_URL);
    public static final Image loading = new Image(Constants.LOADING_URL);

    public static void showAlertWithException(Exception e) {
        Platform.runLater(() -> showAlert(buildAlert(e)));
    }

    public static void showAlert(String info) {
        Platform.runLater(() -> showAlert(buildAlert(info)));
    }

    public static void showAlert(Alert alert) {
        Platform.runLater(alert::show);
    }

    /**
     * buildAlert 需要在 Fx 线程中调用
     */
    public static Alert buildAlert(Exception ex) {
        // http://code.makery.ch/blog/javafx-dialogs-official/
        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage());
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(icon);
        alert.setHeaderText(__("Error"));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();

        Label label = new Label(__("The exception stacktrace was:"));
        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

        alert.getDialogPane().setExpandableContent(expContent);
        alert.getDialogPane().expandedProperty().addListener((invalidationListener) -> Platform.runLater(() -> {
            alert.getDialogPane().requestLayout();
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.sizeToScene();
        }));
        return alert;
    }

    /**
     * buildAlert 需要在 Fx 线程中调用
     */
    public static Alert buildAlert(String info) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, info);
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(icon);
        alert.setHeaderText(__("Note"));
        return alert;
    }

    public static void showWebPage(String url) {
        showWebPage(url, Browser.prefWidth, Browser.prefHeight);
    }

    public static void showWebPage(String url, double prefWidth, double prefHeight) {
        Platform.runLater(() -> new Browser(url, prefWidth, prefHeight).show());
    }

    public static void updateLabel(Label label, String newText) {
        Platform.runLater(() -> label.setText(newText));
    }
}
