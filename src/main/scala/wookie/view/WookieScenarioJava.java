package wookie.view;

import scala.*;
import scala.runtime.BoxedUnit;
import wookie.JQuerySupplier;
import wookie.PanelSupplier;
import wookie.WookiePanel;
import wookie.WookieScenario;

import java.util.Optional;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public final class WookieScenarioJava extends WookieScenario {

    public WookieScenarioJava(String title,
                              Optional<String> url,
                              PanelSupplier panel,
                              WookieSandboxAppJava.WookieScenarioProcedure procedure) {
        super(
                title,
                Option.apply(url.orElse(null)),
                panel,
                new Function3<WookiePanel, WookieView, JQuerySupplier, BoxedUnit>() {
                    @Override
                    public BoxedUnit apply(WookiePanel v1, WookieView v2, JQuerySupplier v3) {
                        procedure.run(v1, v2, v3);
                        return BoxedUnit.UNIT;
                    }

                    @Override
                    public Function1<WookiePanel, Function1<WookieView, Function1<JQuerySupplier, BoxedUnit>>> curried() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Function1<Tuple3<WookiePanel, WookieView, JQuerySupplier>, BoxedUnit> tupled() {
                        throw new UnsupportedOperationException();
                    }
                }
        );
    }
}
