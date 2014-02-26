/*
 * Copyright (C) 2014 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fx;

import chaschev.util.Exceptions;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.VBoxBuilder;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction2;
import scala.runtime.BoxedUnit;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class DownloadFxApp2 extends Application {
    private static final Logger logger = LoggerFactory.getLogger(DownloadFxApp.class);

    public final CountDownLatch downloadLatch = new CountDownLatch(1);
    private static final CountDownLatch appStartedLatch = new CountDownLatch(1);

    protected static final AtomicReference<DownloadFxApp2> instance = new AtomicReference<DownloadFxApp2>();

    public static volatile String version = "7u51";
    public static volatile boolean miniMode = false;

    public static void awaitStart(){
        try {
            appStartedLatch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static String downloadJDKJs() {
        try {
            return Resources.toString(DownloadFxApp.class.getResource("downloadJDK.js"), Charsets.UTF_8);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        createScene(stage);
    }

    public void createScene(Stage stage) {
        try {
            stage.setTitle("Downloading JDK " + version + "...");

            instance.set(this);
            appStartedLatch.countDown();

            final SimpleBrowser2 browser = SimpleBrowser2.newBuilder()
                .useFirebug(false)
                .useJQuery(true)
                .createWebView(!miniMode)
                .build()
                ;

            final ProgressBar progressBar = new ProgressBar(0);
            final Label progressLabel = new Label("Retrieving a link...");

            VBox vBox = VBoxBuilder.create()
                .children(progressLabel, progressBar, browser)
                .fillWidth(true)
                .build();

            Scene scene = new Scene(vBox);

            stage.setScene(scene);

            if(miniMode){
                stage.setWidth(300);
            }else{
                stage.setWidth(1024);
                stage.setHeight(768);
            }

            stage.show();

            VBox.setVgrow(browser, Priority.ALWAYS);

            //noinspection unchecked
            browser.waitForLocation(new AbstractFunction2<String, String, Object>() {
                @Override
                public Boolean apply(String v1, String v2) {
                    return v1.contains("www.oracle.com");
                }
            }, 2000, (Option)Option.apply(new AbstractFunction0<BoxedUnit>() {
                @Override
                public BoxedUnit apply() {
                    System.out.println("navigated to xxx");
                    return BoxedUnit.UNIT;
                }
            }));

            browser.load("http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html", new Runnable() {
                @Override
                public void run() {
                    browser.$("'input'");
                    browser.$("\"input[name='agreementjdk-7u51-oth-JPR']\"");
                    browser.click("\"input[name='agreementjdk-7u51-oth-JPR']\"");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static interface WhenDone{
        void whenDone(boolean found);
    }

    public DownloadFxApp2 awaitDownload(long timeout, TimeUnit unit) throws InterruptedException {
        downloadLatch.await(timeout, unit);
        return this;
    }
}
