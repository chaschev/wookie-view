package fx

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
class MultiStreamCopier {
  /*@volatile
  private var running = true

  private val entries = new mutable.MutableList[CopyEntry]()

  {

  }

  def addEntry(is:InputStream, length:Long) = {
    this.synchronized {
      entries += new CopyEntry(is, length, new SimpleDoubleProperty(0))
    }
  }

  def run() = {
    while(running){

    }
  }*/
}

/*
case class Packet(bytes:Array[Byte], length:Int){}

case class Source(entry:CopyEntry){}

class ReadingActor(bufferSize:Int) extends Actor with ActorLogging {
  private val buffer = new Array[Byte](bufferSize)

  def receive = {
    case Source(entry:CopyEntry) => {
      while(true){
        sender()
        val available = entry.is.available()
        if(available > 0){

        }
      }
    }
  }
}

class WritingActor(os:OutputStream) extends Actor with ActorLogging {
  def receive = {
    case Packet(bytes, length) => os.write(bytes, 0, length)
  }
}

private case class CopyEntry(is:InputStream, length: Long, progress:DoubleProperty){

}

sealed trait DownloaderMessage
case class DownloadFile(uri: URI, file: File) extends DownloaderMessage

object Downloader {
  val dispatcher = Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("pool").build
}

class Downloader extends Actor {
  self.lifeCycle = Permanent
  self.dispatcher = Downloader.dispatcher
  def receive = {
    case DownloadFile(uri, file) =>
    // do the download
  }
}

trait CyclicLoadBalancing extends LoadBalancer { this: Actor =>
  val downloaders: List[ActorRef]
  val seq = new CyclicIterator[ActorRef](downloaders)
}

trait DownloadManager extends Actor {
  self.lifeCycle = Permanent
  self.faultHandler = OneForOneStrategy(List(classOf[Exception]), 5, 5000)
  val downloaders: List[ActorRef]
  override def preStart = downloaders foreach { self.startLink(_) }
  override def postStop = self.shutdownLinkedActors()
}

class DownloadService extends DownloadManager with CyclicLoadBalancing {
  val downloaders = List.fill(3)(Actor.actorOf[Downloader])
}*/
