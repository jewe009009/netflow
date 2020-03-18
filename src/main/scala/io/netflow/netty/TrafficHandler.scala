package io.netflow
package netty

import java.net.InetSocketAddress

import io.netflow.actors._
import io.netflow.lib._
import io.netty.buffer._
import io.netty.channel._
import io.netty.channel.socket.DatagramPacket

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

abstract class TrafficHandler(flowManager: FlowManager, senderManager: SenderManager)
  extends SimpleChannelInboundHandler[DatagramPacket] {

  protected def log: LoggingAdapter
  protected implicit def ec: ExecutionContext

  override def exceptionCaught(ctx: ChannelHandlerContext, e: Throwable): Unit = {
    log.error(e, "Error")
  }

  protected def handOff(actor: ActorRef, sender: InetSocketAddress, buf: ByteBuf): Unit

  override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
    val sender = msg.sender

    // The first two bytes contain the NetFlow version and first four bytes the sFlow version
    if (msg.content().readableBytes() < 4) {
      log.warning("Unsupported UDP Packet received from " + sender.getAddress.getHostAddress + ":" + sender.getPort)
      return
    }

    // Retain the payload
    msg.content().retain()

    // Try to get an actor
    val actor = senderManager.findActorFor(sender.getAddress)
    actor onComplete {
      case Success(actor) => handOff(actor, sender, msg.content())
      case Failure(_) =>
        log.warning("Unauthorized Flow received from " + sender.getAddress.getHostAddress + ":" + sender.getPort)
        /*if (NodeConfig.values.storage.isDefined)*/ flowManager.bad(sender)
    }
  }
}

@ChannelHandler.Sharable
class NetFlowHandler(flowManager: FlowManager, senderManager: SenderManager)(implicit system: ActorSystem)
  extends TrafficHandler(flowManager, senderManager) {

  override protected def log: LoggingAdapter = Logging(system, getClass)

  override protected implicit def ec: ExecutionContext = system.dispatcher

  def handOff(actor: ActorRef, sender: InetSocketAddress, buf: ByteBuf): Unit = {
    actor ! NetFlow(sender, buf)
  }
}

/*
@ChannelHandler.Sharable
object SFlowHandler extends TrafficHandler {
  def handOff(actor: Wactor.Address, sender: InetSocketAddress, buf: ByteBuf): Unit = {
    actor ! SFlow(sender, buf)
  }
}
*/
