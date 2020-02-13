package org.ada.server.dataaccess.ignite

import org.springframework.beans.factory.FactoryBean

@Deprecated
class CurrentThreadClassLoaderFactory extends FactoryBean[ClassLoader] {

  override def getObject: ClassLoader =
    Thread.currentThread().getContextClassLoader()

  override def getObjectType: Class[_] = classOf[ClassLoader]

  override def isSingleton = true
}