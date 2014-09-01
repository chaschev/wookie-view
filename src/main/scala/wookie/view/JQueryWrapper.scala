package wookie.view

import java.util.concurrent.{CountDownLatch, TimeUnit, TimeoutException}
import java.util.{List => JList}
import javafx.concurrent.Worker.State

import netscape.javascript.JSObject
import org.apache.commons.lang3.StringEscapeUtils

import scala.collection.mutable
import scala.util.Random

import scala.collection.JavaConversions._


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
    assign()
    super.interact(script, timeoutMs)
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

  def value(value:String): JQueryWrapper = {
    interact(s"jQuerySetValue($function, '$escapedSelector').val('$value')")
    this
  }

  def submit(): JQueryWrapper = {
    interact(s"submitEnclosingForm($function, '$escapedSelector')")
    this
  }

  protected def _jsJQueryToResultList(r: JSObject): List[JQueryWrapper] =
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

  protected def _jsJQueryToDirectResultList(r: JSObject): List[JQueryWrapper] =
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
    WookieView.logger.debug(s"interact: $script, url=${this.url}, event=$e, " +
      s"currentDocUri: ${wookie.getCurrentDocUri}")

    if(e.isInstanceOf[LoadTimeoutPageDoneEvent]) {
      WookieView.logger.warn(s"warning - interaction is called for timeout event! The result might not be successful!")
    }

    if(wookie.getEngine.getLoadWorker.getState != State.SUCCEEDED) {
      WookieView.logger.warn(s"warning - interaction is called in non-succeeded state: ${wookie.getEngine.getLoadWorker.getState} for interact script $script, interactionId=TODO")
    }

    val interactionId = Random.nextInt()

    val latch = new CountDownLatch(1)

    var r: AnyRef = null

    // todo: remove this line when there is no failure
    val url = wookie.getEngine.getDocument.getDocumentURI

    wookie.includeStuffOnPage(interactionId, url, Some(() => {
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
