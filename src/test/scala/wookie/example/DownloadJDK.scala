package wookie.example

import fx._
import fx.WookieScenario
import java.io.File
import javafx.scene.control.{Label, ProgressBar}
import javafx.application.Platform
import scala.util.Random

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

object DownloadJDK {
  @volatile
  var oracleUser: Option[String] = None

  @volatile
  var oraclePassword: Option[String] = None

  @volatile
  var version: String = "7u51"

  @volatile
  var miniMode: Boolean = false

  @volatile
  var tempDestDir: File = new File(".")

  val progressLabel = new Label("Retrieving a link...")

  val progressBar = new ProgressBar(0)


  val defaultPanel = () => {
    val wookieView = WookieView.newBuilder
      .useJQuery(true)
      .createWebView(!miniMode)
      .build

    val panel = WookiePanel.newBuilder(wookieView)
      .showDebugPanes(!miniMode)
      .showNavigation(!miniMode)
      .build

    val i = 2
    
    panel.getChildren.add(i, progressLabel)
    panel.getChildren.add(i + 1, progressBar)
    
    panel
  }

  def newPanel: WookiePanel = defaultPanel.apply()

  def main(args: Array[String])
  {
    val app = WookieSandboxApp.start()

    app.runOnStage(
      new WookieScenario("http://www.google.com", None,
        defaultPanel,
        (wookiePanel, wookie, $) => {
          $
          val (latestUrl:Option[String], archiveUrl:Option[String]) = getLinksFromVersion
          

          
        }))
  }

  protected def whenSignonForm(wookie:WookieView, $:(String) => JQueryWrapper)
  {
    wookie.waitForLocation(new WaitArg("waiting for signon.jsp")
      .timeoutNone()
      .matchByPredicate((w, arg) => {w.newLoc.contains("signon.jsp")})
      .whenLoaded((event) => {

      println($("#sso_username"))

      $("#sso_username").value(oracleUser.get)
      $("#ssopassword").value(oraclePassword.get)
      $(".sf-btnarea a").clickLink()
    }))
  }


  def getLinksFromVersion:(Option[String], Option[String]) =
  {
    val archiveLinksMap = Map(
      5 -> "http://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-javase5-419410.html",
      6 -> "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase6-419409.html",
      7 -> "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase7-521261.html"
    )

    val latestLinksMap = Map(
      7 -> "http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html"
    )

    var archiveUrl: Option[String] = None
    var latestUrl: Option[String] = None

    val ch = DownloadFxApp3.version.charAt(0)

    if (List('7', '6', '5').contains(ch)) {
      latestUrl = latestLinksMap.get(ch - '0')
      archiveUrl = archiveLinksMap.get(ch - '0')
    }

    (latestUrl, archiveUrl)
  }

}
