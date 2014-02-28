package fx;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.VBoxBuilder;
import javafx.stage.Stage;

public class FXBrowser {
    public static class FxBrowserApp extends Application {
        @Override
        public void start(Stage stage) throws Exception {
            try {
                final WookieView browser = WookieView.newBuilder()
                    .useFirebug(false)
                    .useJQuery(true)
                    .build();

                String initialUrl = "http://www.google.com"; //FXBrowser.class.getResource("http://www.google.com").toExternalForm();

                final TextField location = new TextField(initialUrl);

                Button go = new Button("Go");

                EventHandler<ActionEvent> goAction = new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent arg0) {
                        browser.load(location.getText(), new Runnable() {
                            @Override
                            public void run() {
                                System.out.println(browser.getEngine().executeScript("jQuery('#sso_username')[0]"));
                            }
                        });
                    }
                };

                go.setOnAction(goAction);

                MenuItem menuItem = new MenuItem("Go!");
                menuItem.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN));
                menuItem.setOnAction(goAction);

                browser.getEngine().locationProperty().addListener(new ChangeListener<String>() {
                    @Override
                    public void changed(ObservableValue<? extends String> observableValue, String s, String newLoc) {
                        System.out.println("location changed to: " + newLoc);
                    }
                });

                HBox toolbar = new HBox();
                toolbar.getChildren().addAll(location, go);

                toolbar.setFillHeight(true);

                Menu menu = new Menu("File");

                menu.getItems().addAll(menuItem);

                MenuBar menuBar = new MenuBar();
                menuBar.getMenus().add(menu);

                VBox vBox = VBoxBuilder.create().children(
                    menuBar,
                    toolbar, browser)
                    .fillWidth(true)
                    .build();

                Scene scene = new Scene(vBox);

                stage.setScene(scene);
                stage.setWidth(1024);
                stage.setHeight(768);
                stage.show();

                VBox.setVgrow(browser, Priority.ALWAYS);

                browser.load(initialUrl, new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("-----");
                        System.out.println(browser.$("input").html());
                        System.out.println("-----");
                        System.out.println(browser.$("div").html());
                        System.out.println("-----");
                        System.out.println(browser.$("div").text());
                        System.out.println("-----");
//                        System.out.println(browser.$("html").html());
                        System.out.println("-----");
                        System.out.println(browser.$("a").attr("href"));
                        System.out.println("-----");
                        System.out.println("text:" + browser.$("input[maxlength]").html());
                        browser.
                            $("input[maxlength]")
                            .value("bear java deployment")
                            .submit();
//                        System.out.println("button:" + browser.$("input[type=submit]:first").html());
//                        browser.$("input[type=submit]:first").click(); doesn't work
//                        browser.$("input[maxlength]").pressEnter(); doesn't work
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void main(String[] args) {
            launch(args);
        }
    }
}
