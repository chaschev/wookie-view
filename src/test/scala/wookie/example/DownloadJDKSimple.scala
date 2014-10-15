package wookie.example

import java.util.Properties

import netscape.javascript.JSObject
import org.slf4j.LoggerFactory
import wookie._
import wookie.view._

import scala.concurrent.Future

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object DownloadJDKSimple {
  var login = ""
  var password = ""

  final val logger = LoggerFactory.getLogger(DownloadJDK.getClass)

  def main(args: Array[String])
  {
    val app = WookieSandboxApp.start()

    val props = new Properties()

    val stream = getClass.getResourceAsStream("/auth.properties")

    props.load(stream)

    login = props.getProperty("oracle.login")
    password = props.getProperty("oracle.password")

    app.runOnStage(
      new WookieSimpleScenario(s"Download JDK ${DownloadJDK.version}", DownloadJDK.defaultPanelSupplier) {
        override def run(): Unit = {
          val (latestUrl, archiveUrl) = DownloadJDK.findLinksFromVersion()

          if (latestUrl.isDefined) {
            val jQueryObj = tryFindVersionAtPage(wookie, latestUrl.get)
            if(!jQueryObj.isDefined){
              tryArchivePage(jQueryObj.isDefined, archiveUrl, wookie)
            }else{
              println(s"found the link at ${latestUrl.get}, download should start...")
              addDownloadJDKHook()
              jQueryObj.get.followLink()
            }
          } else {
            tryArchivePage(found = false, archiveUrl, wookie)
          }
        }

        protected def loginAndDownload(){
          if(context.lastEvent.get.asInstanceOf[OkPageDoneEvent].wookieEvent.newLoc.contains("download")){
            logger.info("there was a download, which must have already finished")
          } else {
            logger.info(s"signon form: ${$("#sso_username")}")

            $("#sso_username").value(login)
            $("#ssopassword").value(password)

            $(".submit_btn").followLink()
          }

          load("http://google.com")
        }

        protected def addDownloadJDKHook(): Future[view.DownloadResult] = {
          addDownloadHook(new LocationMatcher(loc =>
            loc.contains("download.oracle.com") && loc.contains("?")
          ))
        }

        private[wookie] def tryFindVersionAtPage(browser: WookieView, archiveUrl: String): Option[JQueryWrapper] = {
          load(archiveUrl)

          try {
            val jQueryObj = FXUtils.execInFxAndAwait(() => {
              browser.getEngine.executeScript("find('" + DownloadJDK.version + "', true, 'linux');").asInstanceOf[JSObject]
            })

            if(jQueryObj == null)
              None
            else
              Some(wrapDomIntoJava(jQueryObj, wookie, archiveUrl))
          } catch {
            case e: Exception => e.printStackTrace()
              None
          }
        }

        protected def tryArchivePage(found: Boolean, archiveUrl: Option[String], browser: WookieView) = {
          if (!found)
            if (archiveUrl.isDefined) {
              val link = tryFindVersionAtPage(browser, archiveUrl.get)

              if (link.isDefined) {
                logger.info("found a link, will be redirected to the login page...")

                addDownloadJDKHook()

                // if there is a download, it will start and lock will freeze execution here
                // if there is no download, it will continue to the login form
                link.get.followLink()
                loginAndDownload()
              } else {
                // todo fixme complete download promise
                // downloadPromise.complete(Try(new DownloadResult(None, "didn't find a link", false)))
              }
            } else {
              logger.error("could not find a link")
            }
          else {
            logger.info("download started...")
            addDownloadJDKHook()
          }
        }

      }.asNotSimpleScenario)
  }
}
