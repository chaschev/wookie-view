package wookie.view

import java.io.{File, FileOutputStream}
import java.lang.{Iterable => JIterable}
import java.net.URL
import java.util.concurrent._
import java.util.{Set => JSet}
import javafx.application.Platform
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.concurrent.Worker
import javafx.concurrent.Worker.State
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
import wookie.{FXUtils, WookieScenario, JQuerySupplier}
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

abstract class WookieNavigationEvent(
    override val pageLoadedId: Int,
    newLoc: String,
    oldLoc: String
) extends WookiePageStateChangedEvent(pageLoadedId)

case class PageReadyEvent(
       override val pageLoadedId: Int,
       newLoc: String,
       oldLoc: String
 ) extends WookieNavigationEvent(pageLoadedId, newLoc, oldLoc)

/**
 * This is used to track a download start. Each time the location url is changed, this event is spawned.
 * Since it is useless most of the time, it is being filtered out in favor of PageReadyEvent.
 */
case class LocationChangedEvent(
   override val pageLoadedId: Int,
   newLoc: String,
   oldLoc: String
) extends WookieNavigationEvent(pageLoadedId, newLoc, oldLoc)


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
  downloadDir: File,
  defaultTimeoutMs: Int
)

class WookieView(builder: WookieBuilder) extends Pane {
  private val random: Random = new Random

  protected val webView: Option[WebView] = if (builder.createWebView) Some(new WebView) else None
  protected val webEngine: WebEngine = if (webView == None) new WebEngine() else webView.get.getEngine

  val options = new WookieViewOptions(
    useFirebug = builder.useFirebug,
    useJQuery = builder.useJQuery,
    includeJsScript = builder.includeJsScript,
    includeJsUrls = builder.includeJsUrls.toList,
    downloadDir = builder.downloadDir,
    defaultTimeoutMs = builder.defaultTimeoutMs
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

  private final val jsInteractionIdsToHandlers = new ConcurrentHashMap[Int, PageOnLoadHandler]

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

          logger.info(s"worker state -> SUCCEEDED, page ready: $currentLocation")

          val pageLoadedId = random.nextInt()

          includeStuffOnPage(pageLoadedId, currentLocation, Some(() => {
            logger.info(s"stuff loaded: $currentLocation")

            val handlers = scanNavRecords(new PageReadyEvent(pageLoadedId, currentLocation, ""))

            logger.debug(s"stateProperty (pageReady): found ${handlers.size} handlers, processing....")

            handlers.foreach(event => {
                event.arg.handleIfDefined(event)
            })
          }))
        } else
        if(newState == Worker.State.FAILED || newState == Worker.State.FAILED ){
          logger.warn(s"worker state is $newState for ${getCurrentDocUri.getOrElse("<empty uri>")}")
        } else {
          logger.debug(s"worker state -> $newState")
        }
      }
    })

    webEngine.locationProperty().addListener(new ChangeListener[String] {
      def changed(observableValue: ObservableValue[_ <: String], oldLoc: String, newLoc: String)
      {
        changedLocationsHistory += newLoc

        val handlers = scanNavRecords(new LocationChangedEvent(random.nextInt(), newLoc, oldLoc))

        handlers.foreach(event => {
          event.arg.handleIfDefined(event)
        })
      }
    })
    
    registerScanningTimer()
  }
  
  private[this] def registerScanningTimer() = {
    scheduler.scheduleAtFixedRate(new Runnable {
      override def run(): Unit = {
        logger.debug("scanning handlers...")
        val handlers = scanNavRecords(new TimerPageStateEvent(random.nextInt(), queriedLocationsHistory.last))

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

  def isPageReady: Boolean = {
    FXUtils.execInFxAndAwait(() => {
      webEngine.getLoadWorker.getState == State.SUCCEEDED
    }, 1000)
  }

  def waitForDownloadToStart(matcher: NavigationMatcher, whenLoaded: Option[WhenPageLoaded] = None): SFuture[DownloadResult] = {
    val downloadPromise = Promise[DownloadResult]()

    waitForLocation(defaultArg(name = "wait for download")
      .timeoutNone()
      .withMatcher(matcher)
      .filterEvents(e => e.isInstanceOf[LocationChangedEvent])
      .whenLoaded(new WhenPageLoaded {
      override def apply()(implicit event: PageDoneEvent): Unit = {
        if(whenLoaded.isDefined) {
          whenLoaded.get.apply()
        }

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

  protected def scanNavRecords(e: WookiePageStateChangedEvent): mutable.MutableList[PageDoneEvent] = {
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
            val eventIsOk = r.arg.acceptsEvent(e)

            if (eventIsOk && r.arg.matcher.matches(r, locEvent)) {
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
   * Waits for a location to change. Just adds a record to an arrays which is scanned with periods of time.
   */
  def waitForLocation(arg: WaitArg): NavigationRecord = {
    logger.info(s"waiting for location, arg=$arg, current location: ${webEngine.getLocation}")

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
    val arg = defaultArg()
    
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

    Platform.runLater(new Runnable {
      override def run(): Unit = webEngine.load(canonizedUrl)

    })

    this
  }

  /**
   * Each time when the page is loaded we need to include complementary stuff like jQuery.
   */
  private[wookie] def includeStuffOnPage(interactionId: Int, location: String, onLoad: Option[() => Unit]): SFuture[Boolean] =
  {
    logger.debug(s"including stuff on page for interactionId $interactionId, location $location")
    try {
      // check a flag showing that stuff is included
      val s = webEngine.executeScript(s"'' + window.__wookiePageInitialized").asInstanceOf[String]
      if (s == "true") {
        logger.debug(s"wookie page $location already initialized")

        if (onLoad.isDefined) onLoad.get.apply()

        return SFuture.successful(true)
      }
    } catch {
      case e: Exception => logger.debug("exception", e)
    }

    logger.info(s"including stuff at location: $location")

    val urls = mutable.MutableList(options.includeJsUrls: _*)

    if (options.useJQuery && !options.useFirebug && !isJQueryAvailableAtPage) {
      urls += s"https://ajax.googleapis.com/ajax/libs/jquery/${WookieView.JQUERY_VERSION}/jquery.min.js"
    }

    var latchCount = 1        // 1 is for wookie.js

    latchCount += urls.length // for all the urls

    if(options.includeJsScript.isDefined) latchCount += 1 // custom JS to include

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

  protected def insertJQuery(interactionId: Int, version: String) {
    if (!isJQueryAvailableAtPage) {
      insertJavaScripts(interactionId, new JSScript(Array[String](s"https://ajax.googleapis.com/ajax/libs/jquery/$version/jquery.min.js"), None))
    } else {
      jsReady(interactionId, "jQuery already loaded")
    }
  }


  protected def isJQueryAvailableAtPage: Boolean =  {
    !webEngine.executeScript(s"typeof jQuery == 'undefined'").asInstanceOf[Boolean]
  }

  protected def insertJavaScripts(interactionId: Int, jsScript: JSScript)
  {
    val jsWindow: JSObject = webEngine.executeScript("window").asInstanceOf[JSObject]

    jsWindow.setMember("simpleBrowser", WookieView.this)

    val isUrlsMode = jsScript.text.isEmpty

    if (isUrlsMode) {
       val script =
         s"""
            |function __loadScripts(array){
            |  alert('loading ' + array.length + ' urls');
            |  for(var i = 0; i < array.length; i++){
            |    var text = array[i];
            |    script = document.createElement('script');
            |
            |
            |    script.onload = function() {
            |      try {
            |        window.simpleBrowser.jsReady($interactionId, 'url ' + text);
            |      } catch(e){
            |        alert(e);
            |      }
            |    };
            |    var head = document.getElementsByTagName('head')[0];
            |
            |    script.type = 'text/javascript';
            |    script.src = text;
            |
            |    head.appendChild(script);
            |  }
            |}
            |__loadScripts([${jsScript.urls.map("'" + _ + "'").mkString(", ")}])""".stripMargin

      webEngine.executeScript(script)
    } else {
      webEngine.executeScript(jsScript.text.get + ";\n" +
        s"window.simpleBrowser.jsReady($interactionId, 'script');")
    }
  }

  def getHTML: String = webEngine.executeScript("document.getElementsByTagName('html')[0].innerHTML").asInstanceOf[String]

  private[wookie] var currentScenario: WookieScenario = null

  /**
   * Todo: change into a wrapper object with methods: html(), text(), attr()
   * @param jQuerySelector
   * @return
   */
  def createJWrapper(jQuerySelector: String, url: String)(implicit e: PageDoneEvent): JQueryWrapper = {
    val sel = StringEscapeUtils.escapeEcmaScript(jQuerySelector)

    val $obj = getEngine.executeScript(s"jQuery('$sel')").asInstanceOf[JSObject]

    //todo this should be extracted into factory ?
    currentScenario.wrapJQueryIntoJava($obj, this, url, Some(jQuerySelector), e)
  }

  def wrapDomIntoJava(dom: JSObject, url: String, selector: Option[String] = None, e: PageDoneEvent): JQueryWrapper = {
    currentScenario.wrapDomIntoJava(dom, this, url, selector, e)
  }

  def wrapJQueryIntoJava($: JSObject, url: String, selector: Option[String] = None, e: PageDoneEvent): JQueryWrapper = {
    currentScenario.wrapJQueryIntoJava($, this, url, selector, e)
  }

  def createJSupplier(url: Option[String], e: Option[PageDoneEvent]) = {
    new JQuerySupplier {
      override def apply(selector: String): JQueryWrapper = {
        val myUrl = url.getOrElse(getEvent.asInstanceOf[OkPageDoneEvent].wookieEvent.newLoc)

        createJWrapper(selector, myUrl)(getEvent)
      }

      override def getEvent: PageDoneEvent = {
        e.getOrElse(super.getEvent)
      }
    }
  }

  def wrapJQuery(delegate: JQueryWrapper, selector:String, url: String): JQueryWrapper = {
    currentScenario.bridgeJQueryWrapper(delegate, selector, url)
  }


  def defaultArg(name: String = ""): WaitArg = new WaitArg(name = name, wookie = this).timeoutMs(options.defaultTimeoutMs)

}