package stub

import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import java.util.concurrent.atomic.AtomicInteger
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.{Actor, ActorSystem, Props, ActorLogging}
import akka.io.{Tcp, IO, TcpPipelineHandler, SslTlsSupport}
import akka.actor.actorRef2Scala
import akka.io.Tcp.Connected
import akka.pattern.ask

object ApplePushStub2 extends App {
  implicit val system = ActorSystem("echo-server2")

  val server = system.actorOf(Props(new EchoServer2), name = "echo-server2")
  implicit val bindingTimeout = Timeout(1.second)
  import system.dispatcher // execution context for the future

  val boundFuture = IO(Tcp) ? Tcp.Bind(server, new InetSocketAddress("localhost", 2195))
  boundFuture.onSuccess { case Tcp.Bound(address) =>
    println("\nBound echo-server2 to " + address)
  }
}

class EchoServer2 extends Actor with ActorLogging {
  import Tcp.{ Connected, Received }
  
  private val counter = new AtomicInteger(0)
  private val sslContext = createSslContext

  def receive: Receive = {
    case c@Connected(remote, _) => {
      log.debug("Connected: " + c + " sender=" + sender)
      
      val init = TcpPipelineHandler.withLogger(log,
          new DecoderStage >>
          new SslTlsSupport(sslEngine(remote, false)))

      val connection = sender
      val handler = context.actorOf(Props(new Actor with ActorLogging {
        def receive = {
          case init.Event(PushData(token, payload)) => {
            val instId = token.substring(0, 32) // in our load tests we pass instId via token
            val payloadRegExp = """.*alert":"(.*)",.*""".r
            val payloadRegExp(msg) = payload
            log.info(msg)
          }
        }
      }))
      
      val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, handler))
      connection ! Tcp.Register(pipeline)
    }
  }
  
  def createSslContext = {
    val keyStore = KeyStore.getInstance("JKS");
	val stream = getClass.getClassLoader.getResourceAsStream("stresstest.jks")
	keyStore.load(stream,
			"1234567".toCharArray());
	val kmf = KeyManagerFactory.getInstance("SunX509");
	kmf.init(keyStore, "1234567".toCharArray());
	
	// protocol may be TLSv1 or SSLv3
    val sc = SSLContext.getInstance("TLSv1");
    sc.init(kmf.getKeyManagers(), null, new java.security.SecureRandom());
    sc
  }
  
  def sslEngine(remote: InetSocketAddress, client: Boolean) = {			
    //val engine = sc.createSSLEngine()
    val engine = sslContext.createSSLEngine(remote.getAddress.getHostAddress(), remote.getPort)
    engine.setUseClientMode(client)
    engine
  }
}

