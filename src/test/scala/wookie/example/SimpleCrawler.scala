package wookie.example

import java.io.File

import org.apache.commons.io.FileUtils
import wookie.example.SimpleCrawlerState.SimpleCrawlerState

//import wookie.example.SimpleCrawlerState.{NOT_STARTED, SimpleCrawlerState}

import scala.collection.mutable.ListBuffer

/**
 * This crawler provides basic data structures for crawling, e.g. entries, states and saving states functionality.
 */
class SimpleCrawler[ENTRY <: SimpleCrawlerEntry](val options: SimpleCrawlerOptions[ENTRY])(implicit m: Manifest[ENTRY]) {
  val defaultPanel = SearchAndStarWookieSimple.defaultPanel
  val state = loadState()

  def start() = {
    state.state = SimpleCrawlerState.IN_PROGRESS
    saveState()
  }

  def complete() = {
    state.state = SimpleCrawlerState.FINISHED
    saveState()
  }

  def saveState() = {
    FileUtils.writeStringToFile(options.stateFile, CrawlerStateObjectJsonHelper.toJson(state))
  }

  private def loadState(): SimpleCrawlerStateObject[ENTRY] = {
    if(!options.stateFile.exists()){
      new SimpleCrawlerStateObject[ENTRY](options)
    } else {
      CrawlerStateObjectJsonHelper.fromJson(FileUtils.readFileToString(options.stateFile))
    }
  }


}

object SimpleCrawlerState extends Enumeration {
  type SimpleCrawlerState = Value
  val NOT_STARTED, IN_PROGRESS, FAILED, FINISHED = Value
}


case class SimpleCrawlerOptions[ENTRY <: SimpleCrawlerEntry] (
  folder: String,
  projectName: String,
  maxSessions:Int = 1
 ) {
  lazy val stateFile: File = new File(folder, projectName + ".json")
}

class SimpleCrawlerEntry(
  var state: SimpleCrawlerState = SimpleCrawlerState.NOT_STARTED,
  var comment: String = ""
)

class SimpleCrawlerStateObject[ENTRY <: SimpleCrawlerEntry] (
  val options: SimpleCrawlerOptions[ENTRY],
  var state: SimpleCrawlerState = SimpleCrawlerState.NOT_STARTED,
  val entries: ListBuffer[ENTRY] = new ListBuffer[ENTRY]()
)

// Fuck Scala. Or fuck JVM whatever
class CrawlerStateObjectJsonHelper[ENTRY <: SimpleCrawlerEntry] (
  val options: SimpleCrawlerOptions[ENTRY],
  val state: SimpleCrawlerState,
  val entries: List[ENTRY])  {

  def toOriginal(): SimpleCrawlerStateObject[ENTRY] =
    new SimpleCrawlerStateObject[ENTRY](options, state, new ListBuffer[ENTRY]() ++= entries)

}

object CrawlerStateObjectJsonHelper {
  def toJson[ENTRY <: SimpleCrawlerEntry](original: SimpleCrawlerStateObject[ENTRY]) =
    JsonObjectUtils.toJson(new CrawlerStateObjectJsonHelper(
      original.options,
      original.state,
      original.entries.toList
    ), pretty = true)

  def fromJson[ENTRY <: SimpleCrawlerEntry](s: String)(implicit m: Manifest[ENTRY]): SimpleCrawlerStateObject[ENTRY] =
    JsonObjectUtils.fromJson[CrawlerStateObjectJsonHelper[ENTRY]](s).toOriginal()
}
