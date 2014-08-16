package wookie.example

import java.io.File
import java.util.Properties
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.layout.VBox

import org.slf4j.LoggerFactory
import wookie.view._
import wookie.{WookiePanel, WookieSandboxApp, WookieScenario}

case class DownloadResult(file:Option[File], message:String, ok:Boolean){

}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object DownloadFxApp3 {
  var login = ""
  var password = ""

  final val logger = LoggerFactory.getLogger(DownloadFxApp3.getClass)

  @volatile
  var version: String = "8u11"

  @volatile
  var miniMode: Boolean = false

  val defaultPanel = () => {
    val wookieView = WookieView.newBuilder
      .useFirebug(false)
      .useJQuery(true)
      .createWebView(!DownloadFxApp3.miniMode)
      .includeJsScript(io.Source.fromInputStream(getClass.getResourceAsStream("/wookie/downloadJDK.js")).mkString)
      .build

    WookiePanel.newBuilder(wookieView)
      .userPanel(new VBox(wookieView.progressLabel, wookieView.progressBar))
      .build
  }

  def main(args: Array[String])
  {
    val app = WookieSandboxApp.start()

    val props = new Properties()

    props.load(getClass.getResourceAsStream("/auth.properties"))

    login = props.getProperty("oracle.login")
    password = props.getProperty("oracle.password")

    app.runOnStage(
      new WookieScenario("http://www.google.com", None,
        defaultPanel,
        (wookiePanel, wookie, $) => {
          val (latestUrl, archiveUrl) = findLinksFromVersion()

          //login form state
          wookie.waitForLocation(new WaitArg()
            .timeoutNone()
            .matchByLocation(_.contains("signon.jsp"))
            .whenLoaded((e) => {

            logger.info(s"signon form: ${$("#sso_username")}")

            $("#sso_username").value(login)
            $("#ssopassword").value(password)

            $(".submit_btn").clickLink()
          }))


          //todo change to location matcher
          wookie.waitForDownloadToStart(
            new PredicateMatcher((e, arg) => {
              val loc = e.newLoc

              loc.contains("download.oracle") && loc.contains("?")
            })
          )

          // download logic
          // if there is 'the latest version page'
          if (latestUrl.isDefined) {
            tryFindVersionAtPage(wookie, latestUrl.get, (found:Boolean) => {
              if(!found){
                tryArchivePage(found, archiveUrl, wookie)
              }else{
                println(s"found the link at ${latestUrl.get}, download should start...")
              }
            })
          } else {
            tryArchivePage(found = false, archiveUrl, wookie)
          }
        }))
  }


//"Downloading JDK " + DownloadFxApp3.version + "..."

  def findLinksFromVersion(): (Option[String], Option[String]) = {
    val archiveLinksMap = Map(
      5 -> "http://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-javase5-419410.html",
      6 -> "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase6-419409.html",
      7 -> "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase7-521261.html",
      8 -> "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-javase8-2177648.html"
    )

    val latestLinksMap = Map(
      7 -> "http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html",
      8 -> "http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html"
    )

    var archiveUrl: Option[String] = None
    var latestUrl: Option[String] = None

    val ch = DownloadFxApp3.version.charAt(0)

    if (List('8', '7', '6', '5').contains(ch)) {
      latestUrl = latestLinksMap.get(ch - '0')
      archiveUrl = archiveLinksMap.get(ch - '0')
    }

    (latestUrl, archiveUrl)
  }

  def setStatus(label:Label, s: String)
  {
    Platform.runLater(new Runnable {
      def run()
      {
        label.setText(s)
      }
    })
  }

  protected def tryArchivePage(found: Boolean, archiveUrl: Option[String], browser: WookieView) = {
    if (!found && archiveUrl.isDefined) {
      tryFindVersionAtPage(browser, archiveUrl.get, (found) => {
        if (found) {
          logger.info("found a link, will be redirected to the login page...")
        } else {
          //todo fixme complete download promise
//          downloadPromise.complete(Try(new DownloadResult(None, "didn't find a link", false)))
        }
      })
    } else {
      logger.info("download started...")
    }
  }

  /**
   *
   * @param browser
   * @param archiveUrl
   * @param whenDone(found Boolean)
   */
  private[wookie] def tryFindVersionAtPage(browser: WookieView, archiveUrl: String, whenDone: (Boolean) => Unit) =
  {
    browser.load(archiveUrl, (event:NavigationEvent) => {
        try {
          val aBoolean = browser.getEngine.executeScript("downloadIfFound('" + DownloadFxApp3.version + "', true, 'linux');").asInstanceOf[Boolean]

          if (aBoolean) {
            whenDone.apply(true)
          }else {
            whenDone.apply(false)
          }
        } catch {
          case e: Exception => e.printStackTrace
        }
      })
  }
}
