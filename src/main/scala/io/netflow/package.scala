package io

import java.net.InetSocketAddress

import io.netflow.flows.cflow
import io.netflow.lib.{FlowPacket, NodeConfig}
import io.netty.buffer.Unpooled

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.KillSwitches
import akka.stream.alpakka.udp.Datagram
import akka.stream.alpakka.udp.scaladsl.Udp
import akka.stream.scaladsl.{Flow, Keep}
import akka.util.ByteString
import scala.concurrent.Future
import scala.util.Try

/**
  * Created by steven on 3/30/2018.
  */
package object netflow {

  def Tryo[T](t: => T): Option[T] = Try(t).toOption

  def netflowReceiver(bindAddress: InetSocketAddress,
                      handler: Flow[FlowPacket, _, _],
                      v9TemplateDAO: NetFlowV9TemplateDAO)(implicit system: ActorSystem): Future[IngestingResult] = {
    import system.dispatcher

    val unwrap = Flow[Datagram].map(d => d.remote -> d.data)
    val parse = netflowParser(unwrap, v9TemplateDAO)

    val flow = parse
      .via(handler)
      .map(_ => Option.empty[Datagram])
      .collect {
        case Some(d) => d
      }
      .viaMat(KillSwitches.single)(Keep.right)

    val (addrF, ks) = Udp.bindFlow(bindAddress).joinMat(flow)(Keep.both).run()
    addrF.map(a => Ingesting(a, ks)).recover {
      case t
          if t
            .isInstanceOf[IllegalArgumentException] && Option(t.getMessage).exists(_.startsWith("Unable to bind to")) =>
        BindFailure
      case t => OtherFailure(t)
    }
  }

  def netflowParser[In, Mat](
      incomingPackets: Flow[In, (InetSocketAddress, ByteString), Mat],
      v9TemplateDAO: NetFlowV9TemplateDAO
  )(implicit system: ActorSystem): Flow[In, FlowPacket, Mat] = {
    import system.dispatcher

    val log = Logging(system, "NetflowParser")
    val templateHolder = new HackyTemplateHolder(v9TemplateDAO)

    val config = NodeConfig.values.netflow
    val threads = config.parsingThreads.getOrElse(Runtime.getRuntime.availableProcessors())

    incomingPackets
      .mapAsync(threads) {
        case (addr, bytes) => templateHolder.handlerFor(addr.getAddress).map(handler => (addr, bytes, handler))
      }
      .mapAsync(threads) {
        case (osender, bytes, handler) =>
          Future.successful {
            val buf = Unpooled.wrappedBuffer(bytes.toArray)

            val ret = Tryo(buf.getUnsignedShort(0)) match {
              case Some(1)  => cflow.NetFlowV1Packet(osender, buf).toOption
              case Some(5)  => cflow.NetFlowV5Packet(osender, buf).toOption
              case Some(6)  => cflow.NetFlowV6Packet(osender, buf).toOption
              case Some(7)  => cflow.NetFlowV7Packet(osender, buf).toOption
              case Some(9)  => cflow.NetFlowV9Packet(osender, buf, handler).toOption
              case Some(10) => log.info("We do not handle NetFlow IPFIX yet"); None
              case _        => None
            }

            buf.release()

            ret
          }
      }
      .collect {
        case Some(p) => p
      }
  }
}
