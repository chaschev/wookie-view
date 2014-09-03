package wookie.example;

import scala.Option;
import wookie.WookiePanel;
import wookie.WookieSandboxApp;
import wookie.WookieSandboxApp$;
import wookie.view.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

public class SearchAndStarWookieJava {
    public static String login = "";
    public static String password = "";

    public static void main(String[] args) throws IOException {
        WookieSandboxApp app = WookieSandboxApp$.MODULE$.start();

        Properties props = new Properties();

        InputStream stream = SearchAndStarWookieJava.class.getResourceAsStream("/auth.properties");

        if(stream == null){
            System.out.println("To run this demo, copy auth.properties.copy into auth.properties and fill in your GitHub auth data.");
            System.exit(-1);
        }

        props.load(stream);

        login = props.getProperty("git.login");
        password = props.getProperty("git.password");

        app.runOnStage(
            new WookieScenarioJava(
                "Star Wookie Page!",
                Optional.of("http://www.google.com"),
                () -> {
                    WookieView wookieView = WookieView$.MODULE$.newBuilder()
                        .useJQuery(true)
                        .build();

                    return WookiePanel.newBuilder(wookieView).build();
                },
                (wookiePanel, wookie, $) -> {
                    // google search result state
                    wookie
                        .waitForLocation(new WaitArg("google search results")
                            .matchByAddress((s) -> s.contains("q="))
                            .whenLoaded(e -> {
                                    System.out.println("results: " + $.apply("h3.r").asResultList());

                                    //find our link in the results list and click it
                                    JQueryWrapper githubLink = $.apply("h3.r a").asResultListJava()
                                        .stream()
                                        .filter((j) -> j.text().contains("chaschev"))
                                        .findFirst().get();

                                    githubLink.followLink(Option.empty());
                                }
                            ));

                    // this matcher is the same as one of the following
                    // there is no problem, because matchers are removed when they are hit
                    // waits for wookie-view page to load and clicks signin button
                    wookie
                        .waitForLocation(new WaitArg("git wookie not logged in")
                        .matchByAddress((s) -> s.contains("/wookie-view"))
                        .whenLoaded(e -> {
                            $.apply("a.button.signin").followLink(Option.empty());
                        }));

                    // login form
                    wookie
                        .waitForLocation(new WaitArg("git login")
                        .matchByAddress((s) -> s.contains("github.com/login"))
                        .whenLoaded(e -> {
                            wookie
                                .waitForLocation(new WaitArg("wookie logged in")
                                .matchByAddress((s) -> s.contains("/wookie-view"))
                                .whenLoaded(e2 -> {
                                    //click 'star' button
                                    JQueryWrapper starButton = $.apply(".star-button:visible");

                                    if (starButton.text().contains("Star")) {
                                        starButton.mouseClick(Option.empty());
                                        System.out.println("Now the star will shine!");
                                    } else {
                                        System.out.println("Invoke me under my stars!");
                                    }
                                }));
                            // fill login data and submit the form
                            $.apply("#login_field").value(login);
                            $.apply("#password").value(password)
                                    .submit(Option.empty());

                        }));


                    // submit google request and start the scenario
                $.apply("input[maxlength]")
                        .value("wookie-view")
                        .submit(Option.empty());
                }
            ));
    }
}