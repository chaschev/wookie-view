package wookie.example

import java.io.{File, FileOutputStream}
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import javafx.application.Platform
import javafx.scene.control.{Label, ProgressBar}
import javafx.scene.layout.VBox

import chaschev.io.FileUtils
import chaschev.lang.LangUtils
import chaschev.util.Exceptions
import com.google.common.io.{ByteStreams, CountingOutputStream}
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.slf4j.LoggerFactory
import wookie.{WookieScenario, WookieSandboxApp, WookiePanel}
import wookie.view._

import scala.concurrent.ops._

case class DownloadResult(file:Option[File], message:String, ok:Boolean){

}

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object DownloadFxApp3 {
  var login = ""
  var password = ""

  var progressLabel: Label = null
  var progressBar: ProgressBar = null

  final val logger = LoggerFactory.getLogger(DownloadFxApp3.getClass)

  final val downloadLatch = new CountDownLatch(1)
  private final val appStartedLatch = new CountDownLatch(1)

  final val downloadResult = new AtomicReference[DownloadResult]

  //  protected final val instance = new AtomicReference[DownloadFxApp3]

  @volatile
  var version: String = "8u11"

  @volatile
  var miniMode: Boolean = false

  @volatile
  var tempDestDir: File = new File(".")

  val defaultPanel = () => {
    val wookieView = WookieView.newBuilder
      .useFirebug(false)
      .useJQuery(true)
      .createWebView(!DownloadFxApp3.miniMode)
      .includeJsScript(io.Source.fromInputStream(getClass.getResourceAsStream("/wookie/downloadJDK.js")).mkString)
      .build

    progressLabel = new Label("Retrieving a link...")
    progressBar = new ProgressBar(0)

    WookiePanel.newBuilder(wookieView)
      .userPanel(new VBox(progressLabel, progressBar))
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
          val (latestUrl:Option[String], archiveUrl: Option[String]) = findLinksFromVersion

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

          whenDownloadStarts(wookie)

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

  private[this] def whenDownloadStarts(wookie: WookieView)
  {
    wookie.waitForLocation(new WaitArg()
      .timeoutNone()
      // todo: change page ready to ANY?
      .matchByPredicate((w, arg) => { w.newLoc.contains("download.oracle") && w.newLoc.contains("?")})
      .isPageReadyEvent(false)
      .whenLoaded((event) => {
      // will be here after
      // clicking accept license and link -> * not logged in * -> here -> download -> redirect to login
      // download -> fill form -> * logged in * -> here -> download
      val navEvent = event.asInstanceOf[OkNavigationEvent]

      val uri = navEvent.wookieEvent.newLoc

      spawn {
        Thread.currentThread().setName("fx-downloader")

        try {
          //there must be a shorter way to do this

          val httpClient = new DefaultHttpClient
          val httpget = new HttpGet(uri)
          val response = httpClient.execute(httpget)

          val code = response.getStatusLine.getStatusCode

          if (code != 200) {
            System.out.println(IOUtils.toString(response.getEntity.getContent))
            throw new RuntimeException("failed to download: " + uri)
          }

          val file = new File(DownloadFxApp3.tempDestDir, StringUtils.substringBefore(FilenameUtils.getName(uri), "?"))

          val httpEntity = response.getEntity
          val length = httpEntity.getContentLength

          val os = new CountingOutputStream(new FileOutputStream(file))

          println(s"Downloading $uri to $file...")

          var lastProgress = 0.0
          var isProgressRunning = true

          spawn {
            Thread.currentThread().setName("progressThread")

            while (isProgressRunning) {
              val bytesCopied = os.getCount
              val progress = bytesCopied * 100D / length

              if (progress != lastProgress) {
                val s = s"${file.getName}: ${FileUtils.humanReadableByteCount(bytesCopied, false, false)}/${FileUtils.humanReadableByteCount(length, false, true)} ${LangUtils.toConciseString(progress, 1)}%"

                setStatus(progressLabel, s)

                print("\r" + s)
              }

              lastProgress = progress
              progressBar.setProgress(bytesCopied * 1D / length)

              try {
                Thread.sleep(500)
              }
              catch {
                case e: InterruptedException => //ignore
              }
            }
          }

          ByteStreams.copy(httpEntity.getContent, os)

          isProgressRunning = false

          System.out.println("Download complete.")
          DownloadFxApp3.downloadResult.set(new DownloadResult(Some(file), "", true))
          DownloadFxApp3.downloadLatch.countDown
        }
        catch {
          case e: Exception =>
            LoggerFactory.getLogger("log").warn("", e)
            DownloadFxApp3.downloadResult.set(new DownloadResult(None, e.getMessage, false))
            throw Exceptions.runtime(e)
        }
      }
    }))
  }

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
          DownloadFxApp3.downloadResult.set(new DownloadResult(None, "didn't find a link", false))

          DownloadFxApp3.downloadLatch.countDown()
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
