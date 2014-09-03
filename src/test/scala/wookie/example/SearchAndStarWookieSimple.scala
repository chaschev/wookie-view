package wookie.example

import java.util.Properties

import wookie._
import wookie.view.WookieView

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object SearchAndStarWookieSimple {
  var login = ""
  var password = ""

  val defaultPanel = new PanelSupplier {
    override def apply(): WookiePanel = {
      val wookieView = WookieView.newBuilder
        .useJQuery(true)
        .build

      WookiePanel.newBuilder(wookieView).build
    }
  }

  def newPanel: WookiePanel = defaultPanel.apply()

  def main(args: Array[String])
  {
    val app = WookieSandboxApp.start()

    val props = new Properties()

    val stream = getClass.getResourceAsStream("/auth.properties")

    if (stream == null) {
      println("To run this demo, copy auth.properties.copy into auth.properties and fill in your GitHub auth data.")
      System.exit(-1)
    }

    props.load(stream)

    login = props.getProperty("git.login")
    password = props.getProperty("git.password")

    app.runOnStage(
      new WookieSimpleScenario("Star Wookie Page!", defaultPanel) {
        override def run(): Unit = {
          load("http://www.google.com")

          $("input[maxlength]")
            .value("wookie-view")
            .submit()

        }
      }.asScenario)
  }
}
