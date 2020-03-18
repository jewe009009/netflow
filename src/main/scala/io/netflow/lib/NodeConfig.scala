package io.netflow
package lib

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import io.wasted.util.InetPrefix

import akka.actor.ActorSystem
import akka.event.Logging
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

private[netflow] object NodeConfig {

  case class ServerConfig(
    cores: Int,
    statuslog: Duration,
//    storage: Option[StorageLayer.Value],
    debugStackTraces: Boolean,
//    admin: AdminConfig,
    netflow: NetFlowConfig,
/*
    sflow: SFlowConfig,
    cassandra: CassandraConfig,
    redis: RedisConfig,
    elastic: ElasticSearchConfig,
    http: HttpConfig,
*/
    tcp: TcpConfig)

/*
  case class AdminConfig(
    authKey: UUID,
    signKey: UUID)

  case class HttpConfig(
    listen: Seq[InetSocketAddress],
    sendFile: Boolean,
    sendBuffer: Boolean,
    gzip: Boolean,
    maxContentLength: Long,
    maxInitialLineLength: Long,
    maxChunkSize: Long,
    maxHeaderSize: Long)

  case class RedisConfig(hosts: Seq[String])
  case class ElasticSearchConfig(hosts: Seq[String])
  case class CassandraConfig(
    hosts: Seq[String],
    keyspace: String,
    minConns: Int,
    maxConns: Int,
    minSimRequests: Int,
    maxSimRequests: Int,
    connectTimeout: Int,
    reconnectTimeout: Int,
    readTimeout: Int,
    keyspaceConfig: String)
*/

  case class TcpConfig(
    sendBufferSize: Integer,
    receiveBufferSize: Integer,
    noDelay: Boolean,
    keepAlive: Boolean,
    reuseAddr: Boolean,
    soLinger: Int)

/*
  case class SFlowConfig(
    listen: Seq[InetSocketAddress],
    persist: Boolean)
*/

  case class NetFlowConfig(
    listen: Seq[InetSocketAddress],
//    persist: Boolean,
    calculateSamples: Boolean,
    extraFields: Boolean)

  private var configs = Map.empty[ActorSystem, ServerConfig]
  private def config(implicit system: ActorSystem): ServerConfig = {
    configs.getOrElse(system, {
      val conf = load
      configs += (system -> conf)
      conf
    })
  }

  private implicit class RichActorSystem(val system: ActorSystem) extends AnyVal {
    private def conf = system.settings.config

    def getString(path: String): Option[String] = Tryo(conf.getString(path))
    def getString(path: String, fallback: String): String = getString(path) getOrElse fallback

    def getInetAddrList(name: String): Option[Seq[InetSocketAddress]] = {
      val valid = Tryo(conf.getStringList(name).asScala.toList) getOrElse Nil flatMap InetPrefix.stringToInetAddr
      if (valid.nonEmpty) Some(valid) else None
    }

    def getInetAddrList(name: String, fallback: Seq[String]): Seq[InetSocketAddress] =
      getInetAddrList(name) getOrElse fallback.flatMap(InetPrefix.stringToInetAddr)

    def getBool(name: String): Option[Boolean] = Tryo(conf.getBoolean(name))
    def getBool(name: String, fallback: Boolean): Boolean = getBool(name) getOrElse fallback

    def getBytes(name: String): Option[Long] = Tryo(conf.getBytes(name).longValue())
    def getBytes(name: String, fallback: Long): Long = getBytes(name) getOrElse fallback

    def getInt(name: String): Option[Int] = Tryo(conf.getInt(name))
    def getInt(name: String, fallback: Int): Int = getInt(name) getOrElse fallback

    def getDuration(name: String): Option[Duration] = Tryo(conf.getDuration(name, TimeUnit.MILLISECONDS).millis)
    def getDuration(name: String, fallback: Duration): Duration = getDuration(name) getOrElse fallback
  }

  private def Config(implicit system: ActorSystem) = new RichActorSystem(system)

  private def load(implicit system: ActorSystem): ServerConfig = {
/*
    val adminAuthKey = Config.getString("admin.authKey").flatMap(ak => Tryo(UUID.fromString(ak))) match {
      case Some(ak) => ak
      case _ =>
        val authKey = UUID.randomUUID()
        error("Invalid or missing UUID at admin.authKey directive. Generated %s", authKey)
        authKey
    }

    val adminSignKey = Config.getString("admin.signKey").flatMap(sk => Tryo(UUID.fromString(sk))) match {
      case Some(sk) => sk
      case _ =>
        val signKey = UUID.randomUUID()
        error("Invalid or missing UUID at admin.signKey directive. Generated %s", signKey)
        signKey
    }

    val admin = AdminConfig(
      authKey = adminAuthKey,
      signKey = adminSignKey)
*/

    val netflow = NetFlowConfig(
      listen = Config.getInetAddrList("netflow.listen", List("0.0.0.0:2055")),
//      persist = Config.getBool("netflow.persist", false),
      calculateSamples = Config.getBool("netflow.calculateSamples", true),
      extraFields = Config.getBool("netflow.extraFields", true))

/*
    val sflow = SFlowConfig(
      listen = Config.getInetAddrList("sflow.listen", List("0.0.0.0:6343")),
      persist = Config.getBool("sflow.persist", false))

    val cassandra = CassandraConfig(
      keyspace = Config.getString("cassandra.keyspace", "netflow"),
      hosts = Config.getStringList("cassandra.hosts", List("localhost")),
      minConns = Config.getInt("cassandra.minConns", 5),
      maxConns = Config.getInt("cassandra.maxConns", 40),
      minSimRequests = Config.getInt("cassandra.minSimRequests", 5),
      maxSimRequests = Config.getInt("cassandra.maxSimRequests", 128),
      connectTimeout = Config.getInt("cassandra.connectTimeout", 5000),
      reconnectTimeout = Config.getInt("cassandra.reconnectTimeout", 5000),
      readTimeout = Config.getInt("cassandra.readTimeout", 60000),
      keyspaceConfig = Config.getString("cassandra.keyspaceConfig",
        "WITH replication = {'class':'SimpleStrategy', 'replication_factor':1}"))
    val redis = RedisConfig(
      hosts = Config.getStringList("redis.hosts", Seq("127.0.0.1:6379")))
    val elastic = ElasticSearchConfig(
      hosts = Config.getStringList("elastic.hosts", Seq("127.0.0.1:8983")))

    val http = HttpConfig(
      listen = Config.getInetAddrList("http.listen", List("0.0.0.0:8080")),
      sendFile = Config.getBool("http.sendFile", true),
      sendBuffer = Config.getBool("http.sendBuffer", true),
      gzip = Config.getBool("http.gzip", true),
      maxContentLength = Config.getBytes("http.maxContentLength", 10 * 1024 * 1024),
      maxInitialLineLength = Config.getBytes("http.maxInitialLineLength", 8 * 1024),
      maxChunkSize = Config.getBytes("http.maxChunkSize", 512 * 1024),
      maxHeaderSize = Config.getBytes("http.maxHeaderSize", 8 * 1024))
    */

    val tcp = TcpConfig(
      sendBufferSize = Config.getBytes("http.tcp.sendBufferSize", 10 * 1024 * 1024).toInt,
      receiveBufferSize = Config.getBytes("http.tcp.receiveBufferSize", 1024 * 1024).toInt,
      noDelay = Config.getBool("http.tcp.noDelay", true),
      keepAlive = Config.getBool("http.tcp.keepAlive", true),
      reuseAddr = Config.getBool("http.tcp.reuseAddr", true),
      soLinger = Config.getInt("http.tcp.soLinger", 0))

//    val storage = Config.getString("storage").flatMap(n => Tryo(StorageLayer.withName(n)))

    val server = ServerConfig(
      cores = Config.getInt("server.cores").getOrElse(Runtime.getRuntime.availableProcessors()),
      statuslog = Config.getDuration("server.statuslog", 10 seconds),
//      storage = storage,
      debugStackTraces = Config.getBool("server.debugStackTraces", true),
//      admin = admin,
      netflow = netflow,
/*
      sflow = sflow,
      cassandra = cassandra,
      redis = redis,
      elastic = elastic,
*/
      tcp = tcp/*,
      http = http*/)
    Logging(system, getClass).info(s"Using ${server.cores} of ${Runtime.getRuntime.availableProcessors()} available cores")
/*
    storage.map { layer =>
      info("You are using the %s storage layer", layer)
    }.getOrElse {
      warn("You are running *WITHOUT* a storage backend, not doing anything!")
    }
*/
    server
  }

//  def reload(): Unit = synchronized(config = load())

  def values(implicit system: ActorSystem): ServerConfig = config

}

