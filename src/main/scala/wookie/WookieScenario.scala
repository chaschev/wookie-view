package wookie

import wookie.view.{JQueryWrapper, WookieView}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class WookieScenario(
                     val title: String,
                     val url: Option[String] = None,
//                     val init: Option[()=>Unit] = None,
                     val panel: PanelSupplier,
                     val procedure: (WookiePanel, WookieView, JQuerySupplier) => Unit)
extends WrapperUtils {
  def newPanel(): WookiePanel = panel()

  override private[wookie] def bridgeJQueryWrapper(delegate: JQueryWrapper, selector: String, url: String): JQueryWrapper = delegate
}
