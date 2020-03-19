package io.netflow

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

import io.netflow.NetFlowReceiverServer.{flowManNamer, sendManNamer}
import io.netflow.actors.{FlowManager, SenderManager}
import io.netflow.lib.NodeConfig
import io.netflow.netty.NetFlowHandler
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel

import akka.actor.ActorSystem
import akka.event.Logging
import akka.util.Helpers
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by steven on 4/2/2018.
  */
object NetFlowReceiverServer {
  private val flowManNamer = Iterator.from(0).map(i => s"flowManager${Helpers.base64(i)}")
  private val sendManNamer = Iterator.from(0).map(i => s"senderManager${Helpers.base64(i)}")
}

class NetFlowReceiverServer(netFlowV9TemplateDAO: NetFlowV9TemplateDAO, netFlowReceiver: NetFlowReceiver,
                            listenAddresses: Option[Seq[InetSocketAddress]] = None)
                           (implicit system: ActorSystem) {
  private val log = Logging(system, getClass)

  private val _eventLoop = new AtomicReference[NioEventLoopGroup](null)
  private def eventLoop = _eventLoop.get()
  private var cleanup = Option.empty[() => Unit]

  def start(): Unit = this.synchronized {
    if (eventLoop == null) {
      log.info("Starting up netflow.io version {}", io.netflow.lib.BuildInfo.version)
      _eventLoop.set(new NioEventLoopGroup)

      val flowManager = new FlowManager(netFlowReceiver, flowManNamer.next())
      val senderManager = new SenderManager(netFlowV9TemplateDAO, flowManager, sendManNamer.next())

      cleanup = Some(() => {
        Await.ready(flowManager.shutdown(), Duration.Inf)
        Await.ready(senderManager.shutdown(), Duration.Inf)
      })

      val netFlowHandler = new NetFlowHandler(flowManager, senderManager)

      listenAddresses.getOrElse(NodeConfig.values.netflow.listen) foreach { addr =>
        val srv = new Bootstrap
        srv.group(eventLoop)
          .localAddress(addr)
          .channel(classOf[NioDatagramChannel])
          .handler(netFlowHandler)
          .option[java.lang.Integer](ChannelOption.SO_RCVBUF, 1500)
        srv.bind().sync
        log.info("Listening for NetFlow on {}:{}", addr.getAddress.getHostAddress, addr.getPort)
      }
    }
  }

  def stop(): Unit = synchronized {
    if (eventLoop != null) {
      log info "Shutting down"

      eventLoop.shutdownGracefully()
      _eventLoop.set(null)

      cleanup.foreach(_())

      log info "Shutdown complete"
    }
  }
}
