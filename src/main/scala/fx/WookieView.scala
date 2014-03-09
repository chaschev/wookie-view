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
import WookieView.logger
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

case class PredicateMatcher(p:((WookieNavigationEvent, WaitArg) => Boolean)) extends NavigationMatcher{
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

class WaitArg(_name:String = ""){
//  var predicate:Option[((String, String, NavArg) => Boolean)] = None
  var timeoutMs: Option[Int] = Some(10000)
  private[this] var handler: Option[(NavigationEvent)=>Unit] = None
  var async: Boolean = true
  var isPageReadyEvent:Boolean = true

  val eventId:Int = Random.nextInt()  //currently not really used

  var startedAtMs:Long = -1
  var location:Option[String] = if(_name == "") None else Some(_name)

  var navigationMatcher:NavigationMatcher = NextPageReadyMatcher.instance

  var name:Option[String] = None

  def timeoutNone():WaitArg = {this.timeoutMs = None; this}
  def timeoutMs(i:Int):WaitArg = {this.timeoutMs = Some(i);this}
  def timeoutSec(sec:Int):WaitArg = {this.timeoutMs = Some(sec * 1000);this}
  def whenLoaded(h:(NavigationEvent)=>Unit):WaitArg = {this.handler = Some(h); this}
  def async(b:Boolean):WaitArg = {this.async = b; this}
  def withName(n:String):WaitArg = {this.name = Some(n); this}

  def matchByLocation(p:(String) => Boolean):WaitArg = {matchByPredicate((e, arg) => {p.apply(e.newLoc)}); this}
  def matchByPredicate(p:((WookieNavigationEvent, WaitArg) => Boolean)):WaitArg = {this.navigationMatcher = new PredicateMatcher(p); this}
  def matchIfPageReady(_location:String):WaitArg = {this.navigationMatcher = NextPageReadyMatcher.instance; this}
  def matchOnlyPageReadyEvent(b:Boolean):WaitArg = {this.isPageReadyEvent = b; this}

  def location(_s:String):WaitArg = {this.location = Some(_s); this}
  def matcher = navigationMatcher

  def isPageReadyEvent(isPageReady:Boolean):WaitArg = {this.isPageReadyEvent = isPageReady; this}

  protected[fx] def handleIfDefined(e:NavigationEvent) = if(this.handler.isDefined) this.handler.get.apply(e)
  
  //todo make package local
  protected[fx] def startedAtMs(t:Long):WaitArg = {this.startedAtMs = t; this}

  def isDue = if(timeoutMs.isEmpty) false else startedAtMs + timeoutMs.get < System.currentTimeMillis()

  def toNavigationRecord:NavigationRecord = {
    new NavigationRecord(this, SettableFuture.create())
  }

  override def toString: String = {
    if(name.isDefined) {
      s"NavArg{'${name.get}'}"
    } else
    if(location.isDefined){
      s"NavArg{'${location.get}'}"
    }
    else {
      super.toString
    }
  }

}


class WookieBuilder {
  var createWebView: Boolean = true
  var useFirebug: Boolean = false
  var useJQuery: Boolean = false
  var includeJsScript: Option[String] = None
  var includeJsUrls: mutable.MutableList[String] = new mutable.MutableList[String]

  def build: WookieView =
  {
    new WookieView(this)
  }

  def createWebView(b: Boolean): WookieBuilder = { createWebView = b; this }
  def useJQuery(b: Boolean): WookieBuilder = { useJQuery = b; this }
  def useFirebug(b: Boolean): WookieBuilder = { useFirebug = b; this }
  def includeJsScript(s: String): WookieBuilder = { includeJsScript = Some(s); this }
  def addScriptUrls(s: String): WookieBuilder = { includeJsUrls += s; this }
}

/**
 * An abstract wrapper for i.e. find in "$(sel).find()" or array items: $($(sel)[3])
 */
abstract class CompositionJQueryWrapper(selector:String, wookie:WookieView) extends JQueryWrapper(selector, wookie){

}

class ArrayItemJQueryWrapper(selector:String, index:Int, wookie:WookieView) extends CompositionJQueryWrapper(selector, wookie){
  val function = s"newArrayFn($index)"
}

class FindJQueryWrapper(selector:String, findSelector:String,  wookie:WookieView) extends CompositionJQueryWrapper(selector, wookie){
//  private final val escapedFindSelector = StringEscapeUtils.escapeEcmaScript(findSelector)
  private final val escapedFindSelector = StringEscapeUtils.escapeEcmaScript(findSelector)
  val function = s"newFindFn('$escapedSelector', '$escapedFindSelector')"
}

class DirectWrapper(isDom:Boolean = false, jsObject:JSObject,  wookie:WookieView) extends CompositionJQueryWrapper("", wookie){
  val function = "directFn"

  private def assign() = {
    val engine = wookie.getEngine
    val window = engine.executeScript("window").asInstanceOf[JSObject]

    if(!isDom){
      window.setMember("__javaToJS", jsObject)
    }else{
      window.setMember("__javaToJS", jsObject)

      engine.executeScript("window.__javaToJS = jQuery(window.__javaToJS); window.__javaToJS")
    }


  }

  override def interact(script: String, timeoutMs: Long): AnyRef = {
    assign()
    super.interact(script, timeoutMs)
  }
}

class SelectorJQueryWrapper(selector:String, wookie:WookieView) extends JQueryWrapper(selector, wookie){
  val function = "jQuery"
}

abstract class JQueryWrapper(selector:String, wookie:WookieView){
  val function:String

  val escapedSelector = StringEscapeUtils.escapeEcmaScript(selector)

  def attr(name: String): String = {
    interact(s"jQueryGetAttr($function, '$escapedSelector', '$name')").asInstanceOf[String]
  }

  def attrs(): List[String] = {
    interact(s"jQueryAttrs($function, '$escapedSelector')").asInstanceOf[List[String]]
  }

  def text(): String = {
    interact(s"jQuery_text($function, '$escapedSelector', false)".toString).asInstanceOf[String]
  }

  def html(): String = {
    interact(s"jQuery_text($function, '$escapedSelector', true)".toString).asInstanceOf[String]
  }

  def clickLink(): JQueryWrapper = {
    interact(s"clickItem($function, '$escapedSelector')".toString)
    this
  }

  def mouseClick(): JQueryWrapper = {
    triggerEvent("click")
  }


  def triggerEvent(event: String): JQueryWrapper ={
    interact(s"$function('$escapedSelector').trigger('$event')".toString)
    this
  }

  def find(findSelector: String): List[JQueryWrapper] = {
    val escapedFindSelector = StringEscapeUtils.escapeEcmaScript(findSelector)

    _jsJQueryToResultList(interact(s"jQueryFind($function, '$escapedSelector', '$escapedFindSelector')").asInstanceOf[JSObject])
  }

  def parent(): List[JQueryWrapper] = {
    _jsJQueryToResultList(interact(s"$function('$escapedSelector').parent()").asInstanceOf[JSObject])
  }


  def attr(name:String, value:String):JQueryWrapper = {
    interact(s"jQuerySetAttr($function, '$escapedSelector', '$name', '$value')")
    this
  }

  def value(value:String):JQueryWrapper = {
    interact(s"jQuerySetValue($function, '$escapedSelector').val('$value')")
    this
  }

  def submit():JQueryWrapper = {
    interact(s"submitEnclosingForm($function, '$escapedSelector')")
    this
  }

  protected def _jsJQueryToResultList(r: JSObject): List[JQueryWrapper] =
  {
    var l = r.getMember("length").asInstanceOf[Int]

    val list = new mutable.MutableList[JQueryWrapper]

    println(s"jQuery object, length=$l")

    for (i <- 0 until l) {
//      list += new ArrayItemJQueryWrapper(selector, i, wookie)
      val slot = r.getSlot(i)

      println(slot, i)

      val sObject = slot.asInstanceOf[JSObject]

      list += new DirectWrapper(true, sObject, wookie)
    }

    list.toList
  }

  protected def _jsJQueryToDirectResultList(r: JSObject): List[JQueryWrapper] =
  {
    var l = r.getMember("length").asInstanceOf[Int]

    val list = new mutable.MutableList[JQueryWrapper]

    println(s"jQuery object, length=$l")

    for (i <- 0 until l) {
      list += new DirectWrapper(true, r.getSlot(l).asInstanceOf[JSObject], wookie)
    }

    list.toList
  }

  def pressKey(code:Int):JQueryWrapper = {
    interact(s"pressKey('$escapedSelector', $code)")
    this
  }

  def pressEnter():JQueryWrapper = {
    pressKey(13)
    this
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

    if(expired){
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
    val r = interact(s"$function('$escapedSelector')").asInstanceOf[JSObject]

    _jsJQueryToResultList(r)
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

  def logOnAlert(message: String)
  {
    LoggerFactory.getLogger("wk-alert").info(message)
  }


  final val logger: Logger = LoggerFactory.getLogger(classOf[WookieView])
}

abstract class NavigationEvent(waitArg:WaitArg) {
  val arg = waitArg
  def ok() : Boolean


}

class NokNavigationEvent(waitArg:WaitArg) extends NavigationEvent(waitArg){
  val ok = false
}

// redesign: need to provide call back, don't need it outside
//
class OkNavigationEvent(_wookieEvent:WookieNavigationEvent, arg:WaitArg) extends NavigationEvent(arg){
  val ok = true
  val wookieEvent = _wookieEvent
}

case class NavigationRecord(arg: WaitArg, future:SettableFuture[NavigationEvent]){

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
        WookieView.logOnAlert(webEvent.getData)
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

        logger.info(s"removed expired wait entry: ${r.arg}, size: ${navigationPredicates.size()}")
      }else{
        val eventTypeOk = r.arg.isPageReadyEvent == w.isPageReadyEvent

        if(eventTypeOk && r.arg.matcher.matches(r, w)){
          val event = new OkNavigationEvent(w, r.arg)
          
          r.future.set(event)
          it.remove()

          matchingEntries += event

          logger.info(s"removed ok entry: ${r.arg}, size: ${navigationPredicates.size()}")
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
  def waitForLocation(arg:WaitArg) : NavigationRecord = {
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

  def load(location: String, onLoad: (NavigationEvent) => Unit):WookieView = {
    load(location, Some(onLoad))
  }

  protected def load(location: String, onLoad: Option[(NavigationEvent) => Unit] = None): WookieView = {
    val arg = new WaitArg()
    
    if(onLoad.isDefined) arg.whenLoaded(onLoad.get)
    
    load(location, arg)
  }

  /**
   * Gives more control options (i.e. customize url matching, set timeout, set sync or async).
   */
  def load(location: String, arg:WaitArg): WookieView =
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
      //callback is not needed actually
      insertJS(eventId, new JSScript(Array.empty, includeJsScript))
    }

    //callback is not needed actually
    initClicksJs(eventId)

    getEngine.executeScript("window.__wookiePageInitialized = true;")

    // when there are no urls and no scripts, latch is 0
    if(latchCount == 0){
      jsReady(eventId, "no-need-to-wait")
    }

    false
  }

  def initClicksJs(eventId: Int)
  {
    val clicksJs = io.Source.fromInputStream(getClass.getResourceAsStream("/fx/clicks.js")).mkString

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
  def $(jQuerySelector: String):JQueryWrapper =
  {
    val sel = StringEscapeUtils.escapeEcmaScript(jQuerySelector)

    val $obj = getEngine.executeScript(s"jQuery('$sel')").asInstanceOf[JSObject]

    new DirectWrapper(false, $obj, this)
  }
}