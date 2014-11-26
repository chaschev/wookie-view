package wookie.example

import java.io.File

import org.apache.commons.io.FileUtils

import scala.collection.mutable.ListBuffer

/**
 * Start the crawler
 * Terminate it
 * Restore from previous state:
 *  deserialize
 *  check if is finished,
 *  find all !FINISHED
 *  process them
 *
 * When Done
 *  sort with comparator
 *  render to html
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
class SimpleCrawler[ENTRY <: SimpleCrawlerEntry](options: SimpleCrawlerOptions[ENTRY]) {
  val defaultPanel = SearchAndStarWookieSimple.defaultPanel
  val state = loadState()

  def start() = {

  }

  def stop() = {

  }

  private def resume() = {

  }

  private def loadState(): SimpleCrawlerStateObject[ENTRY] = {
    if(!options.stateFile.exists()){
      new SimpleCrawlerStateObject[ENTRY](options)
    } else {
      JsonObjectUtils.fromJson[SimpleCrawlerStateObject[ENTRY]](FileUtils.readFileToString(options.stateFile))
    }
  }


}

case class SimpleCrawlerState()

case object NOT_STARTED extends SimpleCrawlerState
case object IN_PROGRESS extends SimpleCrawlerState
case object FAILED extends SimpleCrawlerState // don't try this one again
case object FINISHED extends SimpleCrawlerState

case class SimpleCrawlerOptions[ENTRY <: SimpleCrawlerEntry] (
  folder: File,
  projectName: String,
  maxSessions:Int = 1
 ) {
  lazy val stateFile: File = new File(folder, projectName + ".json")
}

class SimpleCrawlerEntry(
  state: SimpleCrawlerState = NOT_STARTED
)

class SimpleCrawlerStateObject[ENTRY <: SimpleCrawlerEntry] (
  val options: SimpleCrawlerOptions[ENTRY],
  val state: SimpleCrawlerState = NOT_STARTED,
  val entries: ListBuffer[ENTRY] = new ListBuffer[ENTRY]()
)
