package fx

import chaschev.util.Exceptions
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.concurrent.Worker
import javafx.event.EventHandler
import javafx.scene.layout.Pane
import javafx.scene.web.WebEngine
import javafx.scene.web.WebEvent
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent._
import scala.collection.mutable
import org.apache.commons.lang3.{StringUtils, StringEscapeUtils}
import com.google.common.collect.{Sets, Maps}
import java.util
import scala.Some
import com.google.common.util.concurrent.SettableFuture
import scala.collection.JavaConversions._
import scala.util.Random
import java.net.URL
import com.google.common.base.Strings


case class WookieNavigationEvent(newLoc:String, oldLoc:String, isPageReadyEvent:Boolean)

abstract class NavigationMatcher{
  def matches(r:NavigationRecord, w:WookieNavigationEvent):Boolean
}

case class LocationMatcher(url:String) extends NavigationMatcher{
  override def matches(r:NavigationRecord, w:WookieNavigationEvent): Boolean = {
    compareURLs(r.arg.location.get, w.newLoc)
  }

  def compareURLs(_s1: String, _s2: String) = {
    val s1 =  removeLastSlash(new URL(_s1).toExternalForm)
    val s2 =  removeLastSlash(new URL(_s2).toExternalForm)

    s1.equals(s2)
  }

  def removeLastSlash(_s1: String): String =
  {
    if (_s1.endsWith("/")) StringUtils.substringBeforeLast(_s1, "/") else _s1
  }
}

case class PredicateMatcher(p:((WookieNavigationEvent, NavArg) => Boolean)) extends NavigationMatcher{
  override def matches(r: NavigationRecord, w: WookieNavigationEvent): Boolean = {
    p.apply(w, r.arg)
  }
}

object NextPageReadyMatcher{
  val instance = new NextPageReadyMatcher
}

case class NextPageReadyMatcher() extends NavigationMatcher{
  override def matches(r: NavigationRecord, w: WookieNavigationEvent): Boolean = {
    w.isPageReadyEvent
  }
}

class NavArg{
//  var predicate:Option[((String, String, NavArg) => Boolean)] = None
  var timeoutMs: Option[Int] = Some(10000)
  private[this] var handler: Option[(NavigationEvent)=>Unit] = None
  var async: Boolean = true
  var isPageReadyEvent:Boolean = true

  val eventId:Int = Random.nextInt()  //currently not really used

  var startedAtMs:Long = -1
  var location:Option[String] = None

  var navigationMatcher:NavigationMatcher = NextPageReadyMatcher.instance

  def timeoutNone():NavArg = {this.timeoutMs = None; this}
  def timeoutMs(i:Int):NavArg = {this.timeoutMs = Some(i);this}
  def timeoutSec(sec:Int):NavArg = {this.timeoutMs = Some(sec * 1000);this}
  def handler(h:(NavigationEvent)=>Unit):NavArg = {this.handler = Some(h); this}
  def async(b:Boolean):NavArg = {this.async = b; this}

  def matchByLocation(_location:String):NavArg = {this.navigationMatcher = new LocationMatcher(_location); this}
  def matchByPredicate(p:((WookieNavigationEvent, NavArg) => Boolean)):NavArg = {this.navigationMatcher = new PredicateMatcher(p); this}
  def matchIfPageReady(_location:String):NavArg = {this.navigationMatcher = NextPageReadyMatcher.instance; this}
  def matchOnlyPageReadyEvent(b:Boolean):NavArg = {this.isPageReadyEvent = b; this}

  def location(_s:String):NavArg = {this.location = Some(_s); this}
  def matcher = navigationMatcher

  def isPageReadyEvent(isPageReady:Boolean):NavArg = {this.isPageReadyEvent = isPageReady; this}

  protected[fx] def handleIfDefined(e:NavigationEvent) = if(this.handler.isDefined) this.handler.get.apply(e)
  
  //todo make package local
  protected[fx] def startedAtMs(t:Long):NavArg = {this.startedAtMs = t; this}

  def isDue = if(timeoutMs.isEmpty) false else startedAtMs + timeoutMs.get < System.currentTimeMillis()

  def toNavigationRecord:NavigationRecord = {
    new NavigationRecord(this, SettableFuture.create())
  }
}


class WookieBuilder {
  var createWebView: Boolean = true
  var useFirebug: Boolean = true
  var useJQuery: Boolean = false
  var includeJsScript: Option[String] = None
  var includeJsUrls: mutable.MutableList[String] = new mutable.MutableList[String]

  def build: WookieView =
  {
    new WookieView(this)
  }

  def createWebView(b: Boolean): WookieBuilder =
  {
    createWebView = b; this
  }

  def useJQuery(b: Boolean): WookieBuilder =
  {
    useJQuery = b; this
  }

  def useFirebug(b: Boolean): WookieBuilder =
  {
    useFirebug = b; this
  }

  def includeJsScript(s: String): WookieBuilder =
  {
    includeJsScript = Some(s); this
  }

  def addScriptUrls(s: String): WookieBuilder =
  {
    includeJsUrls += s; this
  }

}

class DirectJQueryWrapper(selector:String, index:Int, wookie:WookieView) extends JQueryWrapper(selector, wookie){
  override def attr(name: String): String = {
    interact(s"newArrayFn($index)('$escapedSelector').attr('$name')").asInstanceOf[String]
  }

  override def attrs(): List[String] = {
    interact(s"jQueryAttrs(newArrayFn($index), '$escapedSelector')").asInstanceOf[List[String]]
  }

  override def text(): String = {
    interact(s"jQuery_text(newArrayFn($index), '$escapedSelector', false)".toString).asInstanceOf[String]
  }

  override def html(): String = {
    interact(s"jQuery_text(newArrayFn($index), '$escapedSelector', true)".toString).asInstanceOf[String]
  }

  override def toString: String = {
    html()
  }
}

class JQueryWrapper(selector:String, wookie:WookieView){
  val escapedSelector = StringEscapeUtils.escapeEcmaScript(selector)

  def attrs():List[String] = {
    interact(s"jQueryAttrs(jQuery, '$escapedSelector')").asInstanceOf[List[String]]
  }

  def attr(name:String):String = {
    interact(s"jQuery('$escapedSelector').attr('$name')").asInstanceOf[String]
  }

  def attr(name:String, value:String):JQueryWrapper = {
    interact(s"jQuery('$escapedSelector').attr('$name', '$value')")
    this
  }

  def value(value:String):JQueryWrapper = {
    interact(s"jQuery('$escapedSelector').val('$value')")
    this
  }

  def click():JQueryWrapper = {
    interact(s"$$clickIt('$escapedSelector')")
    this
  }

  def submit():JQueryWrapper = {
    interact(s"submitEnclosingForm('$escapedSelector')")
    this
  }

  def pressKey(code:Int):JQueryWrapper = {
    interact(s"pressKey('$escapedSelector', $code)")
    this
  }

  def pressEnter():JQueryWrapper = {
    pressKey(13)
    this
  }

  def html():String = {
    interact(s"jQuery_text(jQuery, '$escapedSelector', true)".toString).asInstanceOf[String]
//    return "keke"
//    "keke"
  }

  def text():String = {
    interact(s"jQuery_text(jQuery, '$escapedSelector', false)").asInstanceOf[String]
  }

  def interact(script:String, timeoutMs:Long = 5000):AnyRef = {
    WookieView.logger.debug(s"interact: $script")

    val url = wookie.getEngine.getDocument.getDocumentURI
    val eventId = Random.nextInt()

    val latch = new CountDownLatch(1)

    var r:AnyRef = null

    wookie.includeStuffOnPage(eventId, url, Some(() => {
      WookieView.logger.debug(s"executing $script")
      r = wookie.getEngine.executeScript(script)
      latch.countDown()
    }))

    r = wookie.getEngine.executeScript(script)
    latch.countDown()

    val started = System.currentTimeMillis()
    var expired = false

    while(!expired && !latch.await(1000, TimeUnit.MILLISECONDS) ){
      if(System.currentTimeMillis() - started > timeoutMs) expired = true
    }

    if(expired == true){
      throw new TimeoutException(s"JS was not executed in ${timeoutMs}ms")
    }

    WookieView.logger.trace(s"left interact: $script")

    r
  }

  def html(s:String):JQueryWrapper = {

    this
  }

  def append(s:String):JQueryWrapper = {
    this
  }

  def asResultList():List[JQueryWrapper] = {
    println(html())

    val r = interact(s"jQuery('$escapedSelector')").asInstanceOf[JSObject]
    var l = r.getMember("length").asInstanceOf[Int]

    val list = new mutable.MutableList[JQueryWrapper]

    println(l)
    println(r)

    for(i <- 0 until l){
      list += new DirectJQueryWrapper(selector, i, wookie)
    }
//    var l:Long = r.getMember("length").asInstanceOf[Long]

    list.toList
  }

  override def toString: String = html()
}

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

  final val logger: Logger = LoggerFactory.getLogger(classOf[WookieView])
}

abstract class NavigationEvent(waitArg:NavArg) {
  val arg = waitArg
  def ok() : Boolean


}

class NokNavigationEvent(waitArg:NavArg) extends NavigationEvent(waitArg){
  val ok = false
}

// redesign: need to provide call back, don't need it outside
//
class OkNavigationEvent(_wookieEvent:WookieNavigationEvent, arg:NavArg) extends NavigationEvent(arg){
  val ok = true
  val wookieEvent = _wookieEvent
}

case class NavigationRecord(arg: NavArg, future:SettableFuture[NavigationEvent]){

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
  protected val navigationPredicates:util.Set[NavigationRecord] = Sets.newConcurrentHashSet()

  protected val scheduler = Executors.newScheduledThreadPool(4)

  case class JSHandler(eventId: Int, latch: CountDownLatch, handler: Option[() => Unit]) {
//    private val scala = Sets.newConcurrentHashSet().asScala
//    private val map: ConcurrentHashMap[NavigationRecord, Boolean] =

  }

  def getHistory = asJavaIterable(webEngine.getHistory.getEntries.map(x => x.getUrl))

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
        LoggerFactory.getLogger("wk-alert").info(webEvent.getData)
      }
    })

    webEngine.getLoadWorker.stateProperty.addListener(new ChangeListener[Worker.State] {
      def changed(ov: ObservableValue[_ <: Worker.State], t: Worker.State, t1: Worker.State)
      {
        if (t1 eq Worker.State.SUCCEEDED) {
          val currentLocation = webEngine.getDocument.getDocumentURI
          val array = webEngine.getHistory.getEntries

          //todo: get oldLoc from history

          WookieView.logger.info(s"page ready: $currentLocation")
          
          includeStuffOnPage(random.nextInt, currentLocation, Some(() => {
            WookieView.logger.info(s"stuff loaded: $currentLocation")

            val handlers = scanHandlers(new WookieNavigationEvent(currentLocation, "", true))

            WookieView.logger.debug(s"stateProperty (pageReady): found ${handlers.size} handlers, processing....")

            handlers.foreach(event => {
                event.arg.handleIfDefined(event)
            })
          }))
            //todo: provide navigation event which comes from two sources - location & page ready
            //todo: includeStuffOnPage should fix the very old inclusion issue!!
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

  protected def scanHandlers(w:WookieNavigationEvent):mutable.MutableList[NavigationEvent] = {
    val it = navigationPredicates.iterator()

    val matchingEntries:mutable.MutableList[NavigationEvent] = mutable.MutableList()
    
    while(it.hasNext){
      val r = it.next()

      val isDue = r.arg.isDue

      if(isDue) {
        val event = new NokNavigationEvent(r.arg)
        r.future.set(event)
        it.remove()
        
        matchingEntries += event
      }else{
        val eventTypeOk = r.arg.isPageReadyEvent == w.isPageReadyEvent

        if(eventTypeOk && r.arg.matcher.matches(r, w)){
          val event = new OkNavigationEvent(w, r.arg)
          
          r.future.set(event)
          it.remove()
          
          matchingEntries += event 
        }
      }
    }

    matchingEntries
  }

  def getWebView: Option[WebView] = webView

  def getEngine: WebEngine = webEngine

  def jsReady(eventId: Int, from: String)
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
  def waitForLocation(arg:NavArg) : NavigationRecord = {
    val record: NavigationRecord = arg.startedAtMs(System.currentTimeMillis()).toNavigationRecord

    navigationPredicates.add(record)

    if(arg.async) return record

    val orElse: Int = arg.timeoutMs.getOrElse(Int.MaxValue)

    record.future.get(orElse, TimeUnit.MILLISECONDS)

    record
  }

  def waitFor($predicate: String, timeoutMs: Int = 3000): Boolean =
  {
    val startedAt = System.currentTimeMillis
    val eventId = random.nextInt
    val latch = new CountDownLatch(1)

    val waitLatch = new CountDownLatch(1)

    var isOk = false


    jsHandlers.put(eventId, new JSHandler(eventId, latch, Some(() => {
      try {
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
          return false
        }

        val periodMs: Int = timeoutMs / 20 + 2

        var expired = false

        while (!expired && !isOk) {
          val time = System.currentTimeMillis
          if (time - startedAt > timeoutMs) {
            expired = true
          } else {

            var result = false

            val predicateLatch: CountDownLatch = new CountDownLatch(1)

            Platform.runLater(new Runnable {
              def run()
              {
                result = webEngine.executeScript($predicate).asInstanceOf[Boolean]
                predicateLatch.countDown
              }
            })

            predicateLatch.await(periodMs, TimeUnit.MILLISECONDS)

            if (result) isOk = true

            Thread.sleep(periodMs)
          }
        }

        waitLatch.countDown()
      }
      catch {
        case e: InterruptedException => throw Exceptions.runtime(e)
      }
    })))

    Platform.runLater(new Runnable {
      def run()
      {
        insertJQuery(eventId, WookieView.JQUERY_VERSION)
      }
    })

    waitLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    isOk
  }

  def load(location: String): WookieView = load(location, None)

  def load(location: String, r: Runnable): WookieView =
  {
    load(location, Some((event:NavigationEvent) => {
      r.run()
    }))
  }

  def load(location: String, onLoad: Option[(NavigationEvent) => Unit] = None): WookieView = {
    val arg = new NavArg()
    
    if(onLoad.isDefined) arg.handler(onLoad.get)
    
    load(location, arg)
  }

  def load(location: String, arg:NavArg): WookieView =
  {
    WookieView.logger.info("navigating to {}", location)

    //todo test non-canonical urls

    val canonizedUrl = new URL(location).toExternalForm

    arg.location(canonizedUrl)
    
    waitForLocation(arg)

    webEngine.load(canonizedUrl)

    this
  }

  def includeStuffOnPage(eventId: Int, location: String, onLoad: Option[() => Unit]): Boolean =
  {
    try {
      val s = webEngine.executeScript(s"'' + window.__wookiePageInitialized").asInstanceOf[String]
      if (s == "true") {
        WookieView.logger.debug(s"wookie page $location already initialized")

        if (onLoad.isDefined) onLoad.get.apply()

        return true
      }
    } catch {
      case e:Exception => WookieView.logger.debug("exception", e)
    }

    WookieView.logger.info(s"initializing $location")

    val urls = includeJsUrls.clone()

    if (useJQuery && !useFirebug) {
      urls += s"https://ajax.googleapis.com/ajax/libs/jquery/${WookieView.JQUERY_VERSION}/jquery.min.js"
    }

    var latchCount = 0 // 1 is for clicks.js

    latchCount += urls.length // for all the urls

    //    if(includeJsScript.isDefined) latchCount += 1   // for user's JS

    val latch = new CountDownLatch(latchCount)

    val handler = new JSHandler(eventId, latch, onLoad)

    if (jsHandlers.putIfAbsent(eventId, handler) != null) {
      return true
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
      insertJS(eventId, new JSScript(Array.empty, includeJsScript))
    }

    initClicksJs(eventId)

    getEngine.executeScript("window.__wookiePageInitialized = true;")

    false
  }

  def initClicksJs(eventId: Int)
  {
    val clicksJs = io.Source.fromInputStream(getClass.getResourceAsStream("/fx/clicks.js")).mkString

    insertJS(eventId, new JSScript(Array.empty, Some(clicksJs)))
  }

  case class JSScript(urls: Array[String], text: Option[String]) {
    {
      if (!(!urls.isEmpty || text.isDefined)) {
        throw new RuntimeException("url or text must be present")
      }
    }
  }

  protected def insertJQuery(eventId: Int, version: String)
  {
    if (webEngine.executeScript(s"typeof jQuery == 'undefined'").asInstanceOf[Boolean]) {
      insertJS(eventId, new JSScript(Array[String](s"https://ajax.googleapis.com/ajax/libs/jquery/$version/jquery.min.js"), None))
    } else {
      jsReady(eventId, "jQuery already loaded")
    }
  }

  protected def insertJS(eventId: Int, jsScript: JSScript)
  {
    val jsWindow: JSObject = webEngine.executeScript("window").asInstanceOf[JSObject]

    jsWindow.setMember("simpleBrowser", WookieView.this)

    val isUrlsMode = jsScript.text.isEmpty

    val script = if (isUrlsMode) {
      s"" +
        s"" +
        s"function __loadScripts(array){\n" +
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
    } else {
      val text = StringEscapeUtils.escapeEcmaScript(jsScript.text.get)

      s"" +
        s"  script = document.createElement('script');\n\n" +
        s"  \n" +
        s"  script.onload = function() {\n" +
        s"      try{\n      " +
        s"        window.simpleBrowser.jsReady($eventId, 'script');\n" +
        s"      } catch(e){\n" +
        s"        alert(e);\n" +
        s"      }\n" +
        s"  };\n" +
        s"" +
        s"  var head = document.getElementsByTagName('head')[0];\n\n" +
        s"" +
        s"  script.type = 'text/javascript';\n" +
        s"  script.text = '$text';\n" +
        s"  head.appendChild(script);\n"
    }

//    print(s"\n---\n$script\n")

    webEngine.executeScript(script)
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
  def $(jQuerySelector: String):JQueryWrapper =
  {
    new JQueryWrapper(jQuerySelector, this)
  }
}