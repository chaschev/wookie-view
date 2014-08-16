package wookie.view

import java.lang.{Iterable => JIterable}
import java.net.URL
import java.util.concurrent._
import java.util.{Set => JSet}
import javafx.application.Platform
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.concurrent.Worker
import javafx.event.EventHandler
import javafx.scene.layout.Pane
import javafx.scene.web.{WebEngine, WebEvent, WebView}

import com.google.common.collect.Sets
import netscape.javascript.JSObject
import org.apache.commons.lang3.StringEscapeUtils
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future => SFuture}
import scala.util.{Random, Try}


case class WookieNavigationEvent(newLoc: String, oldLoc: String, isPageReadyEvent: Boolean)

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

  def newBuilder: WookieBuilder =
  {
    new WookieBuilder()
  }

  def logOnAlert(message: String)
  {
    LoggerFactory.getLogger("wk-alert").info(message)
  }


  final val logger: Logger = LoggerFactory.getLogger(classOf[WookieView])
}



class WookieView(builder: WookieBuilder) extends Pane {
  private val random: Random = new Random

  protected val webView: Option[WebView] = if (builder.createWebView) Some(new WebView) else None
  protected val webEngine: WebEngine = if (webView == None) new WebEngine() else webView.get.getEngine
  protected val useFirebug = builder.useFirebug
  protected val useJQuery = builder.useJQuery
  protected val includeJsScript: Option[String] = builder.includeJsScript
  protected val includeJsUrls = builder.includeJsUrls

  protected val history = mutable.MutableList[String]()
  protected val navigationPredicates: JSet[NavigationRecord] = Sets.newConcurrentHashSet()

  protected val scheduler = Executors.newScheduledThreadPool(4)

  case class JSHandler(eventId: Int, latch: CountDownLatch, handler: Option[() => Unit]) {

  }

  def getHistory: JIterable[String] = webEngine.getHistory.getEntries.map(x => x.getUrl)

  private final val jsHandlers = new ConcurrentHashMap[Integer, JSHandler]

  {
    if (webView.isDefined) {
      getChildren.add(webView.get)
      webView.get.prefWidthProperty.bind(widthProperty)
      webView.get.prefHeightProperty.bind(heightProperty)
    }

    webEngine.setOnAlert(new EventHandler[WebEvent[String]] {
      def handle(webEvent: WebEvent[String])
      {
        WookieView.logOnAlert(webEvent.getData)
      }
    })

    webEngine.getLoadWorker.stateProperty.addListener(new ChangeListener[Worker.State] {
      def changed(ov: ObservableValue[_ <: Worker.State], t: Worker.State, newState: Worker.State)
      {
        if (newState eq Worker.State.SUCCEEDED) {
          val currentLocation = webEngine.getDocument.getDocumentURI

          //todo: get oldLoc from history

          WookieView.logger.info(s"page ready: $currentLocation")
          
          includeStuffOnPage(random.nextInt(), currentLocation, Some(() => {
            WookieView.logger.info(s"stuff loaded: $currentLocation")

            val handlers = scanHandlers(new WookieNavigationEvent(currentLocation, "", true))

            WookieView.logger.debug(s"stateProperty (pageReady): found ${handlers.size} handlers, processing....")

            handlers.foreach(event => {
                event.arg.handleIfDefined(event)
            })
          }))
            //todo: provide navigation event which comes from two sources - location & page ready
            //todo: includeStuffOnPage should fix the very old inclusion issue!!
        } else
        if(newState == Worker.State.FAILED || newState == Worker.State.FAILED ){
          WookieView.logger.warn(s"worker state is $newState for ${webEngine.getDocument.getDocumentURI}")
        } else {
          WookieView.logger.debug(s"worker state changed to $newState")
        }
      }
    })

    webEngine.locationProperty().addListener(new ChangeListener[String] {
      def changed(observableValue: ObservableValue[_ <: String], oldLoc: String, newLoc: String)
      {
        WookieView.logger.info(s"location changed to $newLoc")

        history += newLoc
        
        val handlers = scanHandlers(new WookieNavigationEvent(newLoc, oldLoc, false))

        handlers.foreach(event => {
          event.arg.handleIfDefined(event)
        })
      }
    })
    
    scheduler.schedule(new Runnable {
      override def run(): Unit = {
        scanHandlers(new WookieNavigationEvent("", "", false))
      }
    }, 50, TimeUnit.MILLISECONDS)
  }


  protected def scanHandlers(w: WookieNavigationEvent): mutable.MutableList[NavigationEvent] = {
    val it = navigationPredicates.iterator()

    val matchingEntries = mutable.MutableList[NavigationEvent]()
    
    while(it.hasNext){
      val r = it.next()

      val isDue = r.arg.isDue

      if(isDue) {
        val event = new NokNavigationEvent(r.arg)

        r.promise.complete(Try(event))
        it.remove()
        
        matchingEntries += event

        WookieView.logger.info(s"removed expired wait entry: ${r.arg}, size: ${navigationPredicates.size()}")
      }else{
        val eventTypeOk = r.arg.isPageReadyEvent == w.isPageReadyEvent

        if(eventTypeOk && r.arg.matcher.matches(r, w)){
          val event = new OkNavigationEvent(w, r.arg)
          
          r.promise.complete(Try(event))
          it.remove()

          matchingEntries += event

          WookieView.logger.info(s"removed ok entry: ${r.arg}, size: ${navigationPredicates.size()}")
        }
      }
    }

    matchingEntries
  }

  def getWebView: Option[WebView] = webView

  def getEngine: WebEngine = webEngine

  private[wookie] def jsReady(eventId: Int, from: String)
  {
    WookieView.logger.info(s"event $eventId arrived from $from")

    val jsHandler = jsHandlers.get(eventId)

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

    WookieView.logger.debug(s"leaving from jsReady, eventId: $eventId")
  }


  /**
   * Waits for a location to change.
   */
  def waitForLocation(arg: WaitArg): NavigationRecord = {
    val record: NavigationRecord = arg.startedAtMs(System.currentTimeMillis()).toNavigationRecord

    navigationPredicates.add(record)

    if(arg.async) return record

    val orElse: Int = arg.timeoutMs.getOrElse(Int.MaxValue)

    Await.result(record.promise.future, Duration(orElse, TimeUnit.MILLISECONDS))

    record
  }

  def load(location: String): WookieView = load(location, None)

  def load(location: String, r: Runnable): WookieView =
  {
    load(location, Some((event:NavigationEvent) => {
      r.run()
    }))
  }

  def load(location: String, onLoad: (NavigationEvent) => Unit):WookieView = {
    load(location, Some(onLoad))
  }

  protected def load(location: String, onLoad: Option[(NavigationEvent) => Unit] = None): WookieView = {
    val arg = new WaitArg()
    
    if(onLoad.isDefined) arg.whenLoaded(onLoad.get)
    
    load(location, arg)
  }

  /**
   * Gives more control options (i.e. customize url matching, set timeout, set sync or async) when loading a page.
   */
  def load(location: String, arg: WaitArg): WookieView =
  {
    WookieView.logger.info("navigating to {}", location)

    //todo test non-canonical urls

    val canonizedUrl = new URL(location).toExternalForm

    arg.location(canonizedUrl)
    
    waitForLocation(arg)

    webEngine.load(canonizedUrl)

    this
  }

  /**
   * Each time when the page is loaded we need to include complementary stuff like jQuery.
   */
  private[wookie] def includeStuffOnPage(eventId: Int, location: String, onLoad: Option[() => Unit]): SFuture[Boolean] =
  {
    try {
      val s = webEngine.executeScript(s"'' + window.__wookiePageInitialized").asInstanceOf[String]
      if (s == "true") {
        WookieView.logger.debug(s"wookie page $location already initialized")

        if (onLoad.isDefined) onLoad.get.apply()

        return SFuture.successful(true)
      }
    } catch {
      case e: Exception => WookieView.logger.debug("exception", e)
    }

    WookieView.logger.info(s"initializing $location")

    val urls = includeJsUrls.clone()

    if (useJQuery && !useFirebug && !isJQueryAvailableAtPage) {
      urls += s"https://ajax.googleapis.com/ajax/libs/jquery/${WookieView.JQUERY_VERSION}/jquery.min.js"
    }

    var latchCount = 1 // 1 is for clicks.js

    latchCount += urls.length // for all the urls

    if(includeJsScript.isDefined) latchCount += 1

    //    if(includeJsScript.isDefined) latchCount += 1   // for user's JS

    val latch = new CountDownLatch(latchCount)

    val handler = new JSHandler(eventId, latch, onLoad)

    if (jsHandlers.putIfAbsent(eventId, handler) != null) {
      return SFuture successful true
    }

    if (onLoad.isDefined) {
      WookieView.logger.info(s"registered event: $eventId for location $location")

      jsHandlers.put(eventId, handler)
    }

    if (useFirebug) {
      webEngine.executeScript("if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}")
    }

    if (urls.nonEmpty) {
      insertJS(eventId, new JSScript(urls.toArray, None))
    }

    if (includeJsScript.isDefined) {
      //callback is not needed actually
      insertJS(eventId, new JSScript(Array.empty, includeJsScript))
    }

    //callback is not needed actually
    initClicksJs(eventId)

    // todo is this all a single thread?
    WookieView.logger.debug("setting window.__wookiePageInitialized = true;")
    getEngine.executeScript("window.__wookiePageInitialized = true;")

    // when there are no urls and no scripts, latch is 0
    if(latchCount == 0){
      jsReady(eventId, "no-need-to-wait")
    }

    SFuture successful false
  }

  def initClicksJs(eventId: Int)
  {
    val clicksJs = io.Source.fromInputStream(getClass.getResourceAsStream("/wookie/clicks.js")).mkString

    insertJS(eventId, new JSScript(Array.empty, Some(clicksJs), Some("clicks.js")))
  }

  case class JSScript(urls: Array[String], text: Option[String], name:Option[String] = None) {
    {
      if (!(!urls.isEmpty || text.isDefined)) {
        throw new RuntimeException("url or text must be present")
      }
    }
  }

  protected def insertJQuery(eventId: Int, version: String)
  {
    if (!isJQueryAvailableAtPage) {
      insertJS(eventId, new JSScript(Array[String](s"https://ajax.googleapis.com/ajax/libs/jquery/$version/jquery.min.js"), None))
    } else {
      jsReady(eventId, "jQuery already loaded")
    }
  }


  protected def isJQueryAvailableAtPage: Boolean =
  {
    !webEngine.executeScript(s"typeof jQuery == 'undefined'").asInstanceOf[Boolean]
  }

  protected def insertJS(eventId: Int, jsScript: JSScript)
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
        s"        window.simpleBrowser.jsReady($eventId, 'url ' + text);\n" +
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
        s"window.simpleBrowser.jsReady($eventId, 'script');")
    }
  }

  def getHTML: String = webEngine.executeScript("document.getElementsByTagName('html')[0].innerHTML").asInstanceOf[String]

  def click(jQuery: String)
  {
    Platform.runLater(new Runnable {
      override def run(): Unit =
      {
        val s = s"$$clickIt($jQuery)"

        println(s"executing $s")

        getEngine.executeScript(s)
      }
    })
  }

  /**
   * Todo: change into a wrapper object with methods: html(), text(), attr()
   * @param jQuerySelector
   * @return
   */
  def $(jQuerySelector: String): JQueryWrapper =
  {
    val sel = StringEscapeUtils.escapeEcmaScript(jQuerySelector)

    val $obj = getEngine.executeScript(s"jQuery('$sel')").asInstanceOf[JSObject]

    new DirectWrapper(false, $obj, this)
  }
}