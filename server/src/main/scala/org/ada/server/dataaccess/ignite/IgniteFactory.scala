package org.ada.server.dataaccess.ignite

import javax.inject.{Inject, Provider, Singleton}
import org.apache.ignite.binary.BinaryTypeConfiguration
import org.apache.ignite.configuration.{BinaryConfiguration, IgniteConfiguration}
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder
import org.apache.ignite.{Ignite, Ignition}

import scala.collection.JavaConversions._

@Singleton
class IgniteFactory @Inject() (serializer: BSONObjectIDBinarySerializer,
                               lifecycleBean: IgniteLifecycleBean,
                               discoverySpi: TcpDiscoverySpi,
                               ipFinder: TcpDiscoveryVmIpFinder) extends Provider[Ignite] {
  override def get(): Ignite = {
    val binaryTypeCfg = new BinaryTypeConfiguration()
    binaryTypeCfg.setTypeName("reactivemongo.bson.BSONObjectID")
    binaryTypeCfg.setSerializer(serializer)

    val binaryCfg = new BinaryConfiguration()
    binaryCfg.setTypeConfigurations(Seq(binaryTypeCfg).toList)

    val cfg = new IgniteConfiguration()
    cfg.setBinaryConfiguration(binaryCfg)
    cfg.setLifecycleBeans(lifecycleBean)

    ipFinder.setAddresses(Seq("127.0.0.1"))
    discoverySpi.setIpFinder(ipFinder)
    cfg.setDiscoverySpi(discoverySpi)

    Ignition.getOrStart(cfg)
  }
}