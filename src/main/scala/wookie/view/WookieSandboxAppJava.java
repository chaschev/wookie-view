package wookie.view;

import wookie.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class WookieSandboxAppJava extends WookieSandboxApp {
    public static WookieSandboxApp start() {
        return WookieSandboxApp$.MODULE$.start();
    }

    public static interface WookieScenarioProcedure {
        void run(WookiePanel panel, WookieView wookie, JQuerySupplier $);
    }

    public void runOnStage(WookieScenarioJava ws){
        super.runOnStage(ws);
    }

}
