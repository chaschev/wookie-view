package wookie

import wookie.view.WookieView

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WookieScenario(
                     val title: String,
                     val url: Option[String] = None,
//                     val init: Option[()=>Unit] = None,
                     val panel: PanelSupplier,
                     val procedure: (WookiePanel, WookieView, JQuerySupplier) => Unit){
  def newPanel(): WookiePanel = panel()
}
