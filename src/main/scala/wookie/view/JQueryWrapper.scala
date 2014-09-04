package wookie.view

import java.util.concurrent.TimeoutException
import java.util.{List => JList}
import javafx.concurrent.Worker.State

import netscape.javascript.JSObject
import org.apache.commons.lang3.StringEscapeUtils
import wookie.FXUtils

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Await, Promise}
import scala.util.{Random, Try}


/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

/**
 * An abstract wrapper for i.e. find in "$(sel).find()" or an array items: $($(sel)[3])
 *
 * Now only the direct wrapper is used which seems to be working stable. So this class might be obsolete.
 */
abstract class CompositionJQueryWrapper(selector: String, wookie: WookieView, url: String, e: PageDoneEvent) extends JQueryWrapper(selector, wookie, url, e){

}

class JQueryWrapperBridge(delegate: JQueryWrapper)
  extends JQueryWrapper(delegate.selector, delegate.wookie, delegate.url, delegate.e) {
  
  val function = delegate.function

  override def attr(name: String): String = { delegate.attr(name) }

  override def attrs(): List[String] = { delegate.attrs() }

  override def text(): String = { delegate.text() }

  override def html(): String = { delegate.html() }

  override def followLink(arg: WaitArg = wookie.defaultArg()): JQueryWrapper = { delegate.followLink(arg); this }

  override def mouseClick(arg: WaitArg = wookie.defaultArg()): JQueryWrapper = { delegate.mouseClick(arg); this }

  override def submit(arg: WaitArg = wookie.defaultArg()): JQueryWrapper = { delegate.submit(arg); this }

  override def triggerEvent(event: String, arg: WaitArg = wookie.defaultArg()): JQueryWrapper = { delegate.triggerEvent(event, arg); this }

  override def find(findSelector: String): List[JQueryWrapper] = { delegate.find(findSelector) }

  override def parent(): List[JQueryWrapper] = { delegate.parent() }

  override def attr(name: String, value: String): JQueryWrapper = { delegate.attr(name, value); this }

  override def value(value: String): JQueryWrapper = { delegate.value(value); this }


  override private[view] def _jsJQueryToResultList(r: JSObject): List[JQueryWrapper] = { delegate._jsJQueryToResultList(r) }

  override private[view] def _jsJQueryToDirectResultList(r: JSObject): List[JQueryWrapper] = { delegate._jsJQueryToDirectResultList(r) }

  override def pressKey(code: Int): JQueryWrapper = { delegate.pressKey(code); this }

  override def pressEnter(): JQueryWrapper = { delegate.pressEnter(); this }

  override def interact(script: String, timeoutMs: Long): AnyRef = { delegate.interact(script, timeoutMs); this }

  override def html(s: String): JQueryWrapper = { delegate.html(s); this }

  override def append(s: String): JQueryWrapper = { delegate.append(s); this }

  override def asResultList(): List[JQueryWrapper] = { delegate.asResultList() }

  override def asResultListJava(): JList[JQueryWrapper] = { delegate.asResultListJava() }

  override def toString: String = delegate.toString
}

class DirectWrapper(isDom: Boolean = false, jsObject: JSObject,  wookie:WookieView, url: String, e: PageDoneEvent) extends CompositionJQueryWrapper("", wookie, url, e){
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
    Await.result(FXUtils.execInFx(() => {
      assign()
      super.interact(script, timeoutMs)
    }), Duration(timeoutMs, "s"))
  }
}

abstract class JQueryWrapper(val selector: String, val wookie: WookieView, val url: String, val e: PageDoneEvent){
  val function: String

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

  def followLink(arg: WaitArg = wookie.defaultArg()): JQueryWrapper = {
    wookie.waitForLocation(arg)

    interact(s"followLink($function, '$escapedSelector')".toString, arg.getTimeoutMs)

    this
  }

  def mouseClick(arg: WaitArg = wookie.defaultArg()): JQueryWrapper = {
    wookie.waitForLocation(arg)

    triggerEvent("click", arg)
  }

  def submit(arg: WaitArg = wookie.defaultArg()): JQueryWrapper = {
    wookie.waitForLocation(arg)

    interact(s"submitEnclosingForm($function, '$escapedSelector')", arg.getTimeoutMs)
    this
  }


  def triggerEvent(event: String, arg: WaitArg = wookie.defaultArg()): JQueryWrapper = {
    interact(s"$function('$escapedSelector').trigger('$event')".toString, arg.getTimeoutMs)
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

  def value(value:String): JQueryWrapper = {
    interact(s"jQuerySetValue($function, '$escapedSelector').val('$value')")
    this
  }


  private[view] def _jsJQueryToResultList(r: JSObject): List[JQueryWrapper] =
  {
    val l = r.getMember("length").asInstanceOf[Int]

    val list = new mutable.MutableList[JQueryWrapper]

    println(s"jQuery object, length=$l")

    for (i <- 0 until l) {
      //      list += new ArrayItemJQueryWrapper(selector, i, wookie)
      val slot = r.getSlot(i)

      println(slot, i)

      val sObject = slot.asInstanceOf[JSObject]

      list += new DirectWrapper(true, sObject, wookie, url, e)
    }

    list.toList
  }

  private[view] def _jsJQueryToDirectResultList(r: JSObject): List[JQueryWrapper] =
  {
    val l = r.getMember("length").asInstanceOf[Int]

    val list = new mutable.MutableList[JQueryWrapper]

    println(s"jQuery object, length=$l")

    for (i <- 0 until l) {
      list += new DirectWrapper(true, r.getSlot(l).asInstanceOf[JSObject], wookie, url, e)
    }

    list.toList
  }

  def pressKey(code:Int): JQueryWrapper = {
    interact(s"pressKey('$escapedSelector', $code)")
    this
  }

  def pressEnter(): JQueryWrapper = {
    pressKey(13)
    this
  }

  def interact(script: String, timeoutMs: Long = 5000): AnyRef = {
    val promise = Promise[AnyRef]()

    FXUtils.execInFx(() => {
      val fut = __interact(script, timeoutMs)
      promise.completeWith(fut)
    })

    try {
      val r = Await.result(promise.future, Duration(timeoutMs, "ms"))
      WookieView.logger.trace(s"left interact: $script")
      r
    } catch{
      case e: TimeoutException =>
        throw new TimeoutException(s"JS was not executed in ${timeoutMs}ms")
    }
  }


  private[this] def __interact(script: String, timeoutMs: Long = 5000): Future[AnyRef] = {
    WookieView.logger.debug(s"interact: $script, url=${this.url}, event=$e, " +
      s"currentDocUri: ${wookie.getCurrentDocUri}")

    if(e.isInstanceOf[LoadTimeoutPageDoneEvent]) {
      WookieView.logger.warn(s"warning - interaction is called for timeout event! The result might not be successful!")
    }

    if(wookie.getEngine.getLoadWorker.getState != State.SUCCEEDED) {
      WookieView.logger.warn(s"warning - interaction is called in non-succeeded state: ${wookie.getEngine.getLoadWorker.getState} for interact script $script, interactionId=TODO")
    }

    val interactionId = Random.nextInt()

    val promise = Promise[AnyRef]()

    // todo: remove this line when there is no failure
    val url = wookie.getEngine.getDocument.getDocumentURI

    val includeStuffLambda = () => {
      wookie.includeStuffOnPage(interactionId, url, Some(() => {
        WookieView.logger.debug(s"executing $script")
        val r = wookie.getEngine.executeScript(script)
        promise.complete(Try(r))
        ()
      }))
    }

     includeStuffLambda()

     promise.future
  }

  def html(s: String):JQueryWrapper = {
    this
  }

  def append(s:String):JQueryWrapper = {
    this
  }

  def asResultList(): List[JQueryWrapper] = {
    val r = interact(s"$function('$escapedSelector')").asInstanceOf[JSObject]

    _jsJQueryToResultList(r)
  }

  def asResultListJava(): JList[JQueryWrapper] = asResultList()

  override def toString: String = html()
}
