package wookie.view

import java.io.{File, FileOutputStream}
import java.lang.{Iterable => JIterable}
import java.net.URL
import java.util.concurrent._
import java.util.{Set => JSet}
import javafx.application.Platform
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.concurrent.Worker
import javafx.event.EventHandler
import javafx.scene.control.{Label, ProgressBar}
import javafx.scene.layout.Pane
import javafx.scene.web.{WebEngine, WebEvent, WebView}

import chaschev.io.FileUtils
import chaschev.lang.LangUtils
import chaschev.util.Exceptions
import com.google.common.collect.Sets
import com.google.common.io.{ByteStreams, CountingOutputStream}
import netscape.javascript.JSObject
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.apache.commons.lang3.{StringEscapeUtils, StringUtils}
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.slf4j.LoggerFactory
import wookie.view.WookieView.logger

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.ops._
import scala.concurrent.{Await, Promise, Future => SFuture}
import scala.util.{Random, Try}


abstract class WookiePageStateChangedEvent(val pageLoadedId: Int){
  def newLoc: String
}

case class WookieNavigationEvent(override val pageLoadedId: Int,
    newLoc: String,
    oldLoc: String,
    isPageReadyEvent: Boolean
) extends WookiePageStateChangedEvent(pageLoadedId)

case class TimerPageStateEvent (
  override val pageLoadedId: Int,
  newLoc: String
) extends WookiePageStateChangedEvent(pageLoadedId)

case class DownloadResult(
   file: Option[File], 
   message: String, 
   ok: Boolean
)

/**
 * WookieBrowser.
 * Update JDK downloader.
 * (ok) Add a script to a header.
 * (ok) Click a button.
 * (ok) Wait for an element to appear.
 * (ok) Check if jQuery is added.
 * (ok) Wait for navigation event.
 */
object WookieView {
  final val JQUERY_VERSION = "1.9.1"

  def newBuilder: WookieBuilder = {
    new WookieBuilder()
  }

  def logOnAlert(message: String) {
    LoggerFactory.getLogger("wk-alert").info(message)
  }

  final val logger = LoggerFactory.getLogger(classOf[WookieView])
}


case class WookieViewOptions(
  useFirebug: Boolean,
  useJQuery: Boolean,
  includeJsScript: Option[String],
  includeJsUrls: List[String],
  downloadDir: File
)

class WookieView(builder: WookieBuilder) extends Pane {
  private val random: Random = new Random

  protected val webView: Option[WebView] = if (builder.createWebView) Some(new WebView) else None
  protected val webEngine: WebEngine = if (webView == None) new WebEngine() else webView.get.getEngine

  protected val options = new WookieViewOptions(
    useFirebug = builder.useFirebug,
    useJQuery = builder.useJQuery,
    includeJsScript = builder.includeJsScript,
    includeJsUrls = builder.includeJsUrls.toList,
    downloadDir = builder.downloadDir
  )

  val progressLabel = new Label("")
  val progressBar = new ProgressBar(0)

  protected val changedLocationsHistory = mutable.MutableList[String]()
  protected val queriedLocationsHistory = mutable.MutableList[String]()
  protected val navigationRecords: JSet[NavigationRecord] = Sets.newConcurrentHashSet()

  protected val scheduler = Executors.newScheduledThreadPool(4)

  case class PageOnLoadHandler(interactionId: Int, latch: CountDownLatch, handler: Option[() => Unit]) {

  }

  def getHistory: JIterable[String] = webEngine.getHistory.getEntries.map(x => x.getUrl)

  private final val jsInteractionIdsToHandlers = new ConcurrentHashMap[Integer, PageOnLoadHandler]

  {
    if (webView.isDefined) {
      getChildren.add(webView.get)
      webView.get.prefWidthProperty.bind(widthProperty)
      webView.get.prefHeightProperty.bind(heightProperty)
    }

    webEngine.setOnAlert(new EventHandler[WebEvent[String]] {
      def handle(webEvent: WebEvent[String]) {
        WookieView.logOnAlert(webEvent.getData)
      }
    })

    webEngine.getLoadWorker.stateProperty.addListener(new ChangeListener[Worker.State] {
      def changed(ov: ObservableValue[_ <: Worker.State], t: Worker.State, newState: Worker.State)
      {
        if (newState eq Worker.State.SUCCEEDED) {
          val currentLocation = webEngine.getDocument.getDocumentURI

          //todo: get oldLoc from history

          logger.info(s"page ready: $currentLocation")

          val pageLoadedId = random.nextInt()

          includeStuffOnPage(pageLoadedId, currentLocation, Some(() => {
            logger.info(s"stuff loaded: $currentLocation")

            val handlers = scanHandlers(new WookieNavigationEvent(pageLoadedId, currentLocation, "", true))

            logger.debug(s"stateProperty (pageReady): found ${handlers.size} handlers, processing....")

            handlers.foreach(event => {
                event.arg.handleIfDefined(event)
            })
          }))
            //todo: provide navigation event which comes from two sources - location & page ready
            //todo: includeStuffOnPage should fix the very old inclusion issue!!
        } else
        if(newState == Worker.State.FAILED || newState == Worker.State.FAILED ){
          logger.warn(s"worker state is $newState for ${webEngine.getDocument.getDocumentURI}")
        } else {
          logger.debug(s"worker state changed to $newState")
        }
      }
    })

    webEngine.locationProperty().addListener(new ChangeListener[String] {
      def changed(observableValue: ObservableValue[_ <: String], oldLoc: String, newLoc: String)
      {
        logger.info(s"location changed to $newLoc")

        changedLocationsHistory += newLoc
        
//        val handlers = scanHandlers(new WookieNavigationEvent(random.nextInt(), newLoc, oldLoc, false))
//
//        handlers.foreach(event => {
//          event.arg.handleIfDefined(event)
//        })
      }
    })
    
    scheduler.scheduleAtFixedRate(new Runnable {
      override def run(): Unit = {
        logger.debug("scanning handlers...")
        val handlers = scanHandlers(new TimerPageStateEvent(random.nextInt(), queriedLocationsHistory.last))

        handlers.foreach(event => {
          event.arg.handleIfDefined(event)
        })
      }
    }, 50, 50, TimeUnit.MILLISECONDS)
  }

  def getCurrentDocUri: Option[String] = {
    if(webEngine.getDocument == null)
      None
    else
      Option(webEngine.getDocument.getDocumentURI)
  }

  def waitForDownloadToStart(matcher: NavigationMatcher): SFuture[DownloadResult] = {
    val downloadPromise = Promise[DownloadResult]()

    waitForLocation(new WaitArg()
      .timeoutNone()
      .withMatcher(matcher)
      .isPageReadyEvent(false)
      .whenLoaded(new WhenPageLoaded {
      override def apply()(implicit event: PageDoneEvent): Unit = {
        //copy-pasted magic
        val navEvent = event.asInstanceOf[OkPageDoneEvent]

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

            val file = new File(options.downloadDir, StringUtils.substringBefore(FilenameUtils.getName(uri), "?"))

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

                  setDownloadStatus(progressLabel, s)

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

            logger.info("download complete")

            downloadPromise.complete(Try(new DownloadResult(Some(file), "", true)))
          }
          catch {
            case e: Exception =>
              LoggerFactory.getLogger("log").warn("", e)
              downloadPromise.complete(Try(new DownloadResult(None, e.getMessage, false)))
              throw Exceptions.runtime(e)
          }
        }
      }
    }))

    downloadPromise.future
  }

  private[this] def setDownloadStatus(label: Label, s: String)
  {
    Platform.runLater(new Runnable {
      def run()
      {
        label.setText(s)
      }
    })
  }


  protected def scanHandlers(e: WookiePageStateChangedEvent): mutable.MutableList[PageDoneEvent] = {
    logger.debug(s"scanning ${navigationRecords.size()} records...")

    val it = navigationRecords.iterator()

    val matchingEntries = mutable.MutableList[PageDoneEvent]()
    
    while(it.hasNext){
      val r = it.next()

      val isDue = r.arg.isDue

      if(isDue) {
        val event = new LoadTimeoutPageDoneEvent(r.arg)

        r.promise.complete(Try(event))
        it.remove()
        
        matchingEntries += event

        logger.info(s"timed out after ${r.arg.timeoutMs}ms, removing expired wait entry: ${r.arg}, size: ${navigationRecords.size()}")
      }else{
        e match {
          // predicates work only for non-timer events
          // so we wait for a page to load before making checks
          case locEvent: WookieNavigationEvent =>
            val eventTypeOk = r.arg.isPageReadyEvent == locEvent.isPageReadyEvent

            if (eventTypeOk && r.arg.matcher.matches(r, locEvent)) {
              val event = new OkPageDoneEvent(e, r.arg)

              r.promise.complete(Try(event))
              it.remove()

              matchingEntries += event

              logger.info(s"removed ok entry: ${r.arg}, new size: ${navigationRecords.size()}")
            }
          case _ =>
            false
        }
      }
    }

    matchingEntries
  }

  def getWebView: Option[WebView] = webView

  def getEngine: WebEngine = webEngine

  private[wookie] def jsReady(interactionId: Int, from: String) {
    logger.info(s"event $interactionId arrived from $from")

    val jsHandler = jsInteractionIdsToHandlers.get(interactionId)

    if (jsHandler != null) {
      jsHandler.latch.countDown()

      if (jsHandler.latch.getCount == 0) {
        this.synchronized {
          if (jsHandler.latch.getCount == 0) {
            if (jsHandler.handler.isDefined) jsHandler.handler.get()
          }
        }
      }
    }

    logger.debug(s"leaving from jsReady, interactionId: $interactionId")
  }


  /**
   * Waits for a location to change.
   */
  def waitForLocation(arg: WaitArg): NavigationRecord = {
    val record: NavigationRecord = arg.startedAtMs(System.currentTimeMillis()).toNavigationRecord

    navigationRecords.add(record)

    if(arg.async) return record

    val orElse: Int = arg.timeoutMs.getOrElse(Int.MaxValue)

    Await.result(record.promise.future, Duration(orElse, TimeUnit.MILLISECONDS))

    record
  }

  def load(location: String): WookieView = load(location, None)

  def load(location: String, r: Runnable): WookieView = {
    load(location, Some(new WhenPageLoaded {
      override def apply()(implicit e: PageDoneEvent): Unit = {
        r.run()
      }
    }))
  }

  def load(location: String, onLoad: WhenPageLoaded):WookieView = {
    load(location, Some(onLoad))
  }

  protected def load(location: String, onLoad: Option[WhenPageLoaded] = None): WookieView = {
    val arg = new WaitArg()
    
    if(onLoad.isDefined) arg.whenLoaded(onLoad.get)
    
    load(location, arg)
  }

  /**
   * Gives more control options (i.e. customize url matching, set timeout, set sync or async) when loading a page.
   */
  def load(location: String, arg: WaitArg): WookieView =
  {
    logger.info("navigating to {}", location)

    //todo test non-canonical urls

    val canonizedUrl = new URL(location).toExternalForm

    arg.location(canonizedUrl)
    
    waitForLocation(arg)

    queriedLocationsHistory += canonizedUrl
    webEngine.load(canonizedUrl)

    this
  }

  /**
   * Each time when the page is loaded we need to include complementary stuff like jQuery.
   */
  private[wookie] def includeStuffOnPage(interactionId: Int, location: String, onLoad: Option[() => Unit]): SFuture[Boolean] =
  {
    logger.debug(s"including stuff on page for interactionId $interactionId, location $location")
    try {
      val s = webEngine.executeScript(s"'' + window.__wookiePageInitialized").asInstanceOf[String]
      if (s == "true") {
        logger.debug(s"wookie page $location already initialized")

        if (onLoad.isDefined) onLoad.get.apply()

        return SFuture.successful(true)
      }
    } catch {
      case e: Exception => logger.debug("exception", e)
    }

    logger.info(s"initializing $location")

    val urls = mutable.MutableList(options.includeJsUrls: _*)

    if (options.useJQuery && !options.useFirebug && !isJQueryAvailableAtPage) {
      urls += s"https://ajax.googleapis.com/ajax/libs/jquery/${WookieView.JQUERY_VERSION}/jquery.min.js"
    }

    var latchCount = 1 // 1 is for wookie.js

    latchCount += urls.length // for all the urls

    if(options.includeJsScript.isDefined) latchCount += 1

    //    if(includeJsScript.isDefined) latchCount += 1   // for user's JS

    val latch = new CountDownLatch(latchCount)

    val handler = new PageOnLoadHandler(interactionId, latch, onLoad)

    if (jsInteractionIdsToHandlers.putIfAbsent(interactionId, handler) != null) {
      return SFuture successful true
    }

    if (onLoad.isDefined) {
      logger.info(s"registered event: $interactionId for location $location")

      jsInteractionIdsToHandlers.put(interactionId, handler)
    }

    if (options.useFirebug) {
      webEngine.executeScript("if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}")
    }

    if (urls.nonEmpty) {
      insertJavaScripts(interactionId, new JSScript(urls.toArray, None))
    }

    if (options.includeJsScript.isDefined) {
      //callback is not needed actually
      insertJavaScripts(interactionId, new JSScript(Array.empty, options.includeJsScript))
    }

    //callback is not needed actually
    initClicksJs(interactionId)

    // todo is this all a single thread?
    logger.debug("setting window.__wookiePageInitialized = true;")
    getEngine.executeScript("window.__wookiePageInitialized = true;")

    // when there are no urls and no scripts, latch is 0
    if(latchCount == 0){
      jsReady(interactionId, "no-need-to-wait")
    }

    SFuture successful false
  }

  def initClicksJs(interactionId: Int)
  {
    val wookieJs = io.Source.fromInputStream(getClass.getResourceAsStream("/wookie/wookie.js")).mkString

    insertJavaScripts(interactionId, new JSScript(Array.empty, Some(wookieJs), Some("wookie.js")))
  }

  case class JSScript(urls: Array[String], text: Option[String], name:Option[String] = None) {
    {
      if (!(!urls.isEmpty || text.isDefined)) {
        throw new RuntimeException("url or text must be present")
      }
    }
  }

  protected def insertJQuery(interactionId: Int, version: String)
  {
    if (!isJQueryAvailableAtPage) {
      insertJavaScripts(interactionId, new JSScript(Array[String](s"https://ajax.googleapis.com/ajax/libs/jquery/$version/jquery.min.js"), None))
    } else {
      jsReady(interactionId, "jQuery already loaded")
    }
  }


  protected def isJQueryAvailableAtPage: Boolean =
  {
    !webEngine.executeScript(s"typeof jQuery == 'undefined'").asInstanceOf[Boolean]
  }

  protected def insertJavaScripts(interactionId: Int, jsScript: JSScript)
  {
    val jsWindow: JSObject = webEngine.executeScript("window").asInstanceOf[JSObject]

    jsWindow.setMember("simpleBrowser", WookieView.this)

    val isUrlsMode = jsScript.text.isEmpty

    if (isUrlsMode) {
       val script =s"" +
        s"" +
        s"function __loadScripts(array){\n" +
        s"  alert('loading ' + array.length + ' urls');\n" +
        s"  for(var i = 0; i < array.length; i++){\n" +
        s"    var text = array[i];\n" +
        s"    script = document.createElement('script');\n\n" +
        s"    \n" +
        s"    script.onload = function() {\n" +
        s"      try{\n      " +
        s"        window.simpleBrowser.jsReady($interactionId, 'url ' + text);\n" +
        s"      } catch(e){\n" +
        s"        alert(e);\n" +
        s"      }\n" +
        s"    };\n" +
        s"    " +
        s"    var head = document.getElementsByTagName('head')[0];\n\n" +
        s"    " +
        s"    script.type = 'text/javascript';\n" +
        s"    script.src = text;\n\n" +
        s"    head.appendChild(script);\n" +
        s"  }\n" +
        s"}\n" +
        s"__loadScripts([" + jsScript.urls.map("'" + _ + "'").mkString(", ") + s"])"

      webEngine.executeScript(script)
    } else {
      webEngine.executeScript(jsScript.text.get + ";\n" +
        s"window.simpleBrowser.jsReady($interactionId, 'script');")
    }
  }

  def getHTML: String = webEngine.executeScript("document.getElementsByTagName('html')[0].innerHTML").asInstanceOf[String]

  def click(jQuery: String)
  {
    Platform.runLater(new Runnable {
      override def run(): Unit =
      {
        val s = s"clickJquerySelector($jQuery)"

        println(s"executing $s")

        getEngine.executeScript(s)
      }
    })
  }

//  added nav event to the $
//  now I need to add debug info to interact method

  /**
   * Todo: change into a wrapper object with methods: html(), text(), attr()
   * @param jQuerySelector
   * @return
   */
  def $(jQuerySelector: String, url: String)(implicit e: PageDoneEvent): JQueryWrapper =
  {
    val sel = StringEscapeUtils.escapeEcmaScript(jQuerySelector)

    val $obj = getEngine.executeScript(s"jQuery('$sel')").asInstanceOf[JSObject]

    new DirectWrapper(false, $obj, this, url, e)
  }
}