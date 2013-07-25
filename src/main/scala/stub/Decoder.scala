package stub

import akka.io._
import java.nio.ByteOrder
import akka.util.ByteString
import scala.annotation.tailrec
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.commons.codec.binary.Hex

case class PushData(token: String, payload: String)

class DecoderStage extends PipelineStage[PipelineContext, Tcp.Command, Tcp.Command, PushData, Tcp.Event] with Logging {
  
  override def apply(ctx: PipelineContext) =
    new PipePair[Tcp.Command, Tcp.Command, PushData, Tcp.Event] {
      var buffer: Option[ByteString] = None
      implicit val byteOrder = ByteOrder.BIG_ENDIAN 

      @tailrec
      def extractFrames(bs: ByteString, acc: List[PushData]): (Option[ByteString], Seq[PushData]) = {
        if (bs.isEmpty) {
          (None, acc)
        } else if (bs.length < 11) {
          (Some(bs.compact), acc)
        } else {
          val it = bs.iterator
          it.drop(1)
          val id = it.getInt
          val ts = it.getInt
          val tokenLength = it.getShort
          if (bs.length < 11 + tokenLength + 2) {
            (Some(bs.compact), acc)
          } else {
            val token = Hex.encodeHexString(bs.slice(11, 11 + tokenLength).toArray)
            //println("token=" + token)
            it.drop(tokenLength)
            val payloadLength = it.getShort
            if (bs.length < 11 + tokenLength + 2 + payloadLength) {
              (Some(bs.compact), acc)
            } else {
              val payload = bs.slice(11 + tokenLength + 2, 11 + tokenLength + 2 + payloadLength).utf8String
              //println("payload=" + payload)
              val (frame, tail) = bs.splitAt(11 + tokenLength + 2 + payloadLength)
              val pushData = PushData(token, frame.drop(11 + tokenLength + 2).utf8String)
              extractFrames(tail,  pushData :: acc)
            }
          }
        }
      }

      override def commandPipeline = (cmd: Tcp.Command) => Seq()

      override def eventPipeline = (ev: Tcp.Event) => {
        // println("ev=" + ev)

        ev match {
          case Tcp.Received(bs) => {
            val data = if (buffer.isEmpty) bs else buffer.get ++ bs
            val (nb, frames) = extractFrames(data, Nil)
            buffer = nb
            frames match {
              case Nil => Nil
              case one :: Nil => ctx.singleEvent(one)
              case many => many.reverseMap(Left(_))
            }
          }
          case someEv => println("Some other event: " + someEv); Nil // ctx.singleEvent(ev)
        }
        
      }
    }
}