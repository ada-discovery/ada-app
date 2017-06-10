package com.banda

import java.{lang => jl}

import com.banda.core.metrics.MetricsFactory
import com.banda.function.enumerator.ListEnumeratorFactory
import com.banda.network.business.TopologyFactory
import com.banda.network.metrics.{DoubleConvertibleMetricsFactory, DoubleMetricsFactory}
import com.google.inject.Provides
import com.google.inject.spring.SpringIntegration
import net.codingwell.scalaguice.ScalaModule
import org.springframework.context.support.ClassPathXmlApplicationContext

class InCalSpringModule extends ScalaModule {

  @Provides @Singleton
  def createDoubleMetricsFactory(listEnumeratorFactory: ListEnumeratorFactory): MetricsFactory[jl.Double] =
    new DoubleMetricsFactory(listEnumeratorFactory)

  def createIntegerMetricsFactory(doubleMetricFactory: MetricsFactory[jl.Double]): MetricsFactory[jl.Integer]  =
    new DoubleConvertibleMetricsFactory[jl.Integer](doubleMetricFactory, classOf[jl.Integer])

  def createTopologyFactory(integerMetricsFactory: MetricsFactory[jl.Integer]): TopologyFactory =
    new TopologyFactoryImpl(integerMetricsFactory)


  //      <bean id="topologyFactory" class="com.banda.network.business.TopologyFactoryImpl">
  //      <constructor-arg ref="integerMetricsFactory"/>
  //    </bean>

  override def configure = {
//
//      <bean id="listEnumeratorFactory" class="com.banda.function.enumerator.ListEnumeratorFactoryImpl"/>
//
//    bind[ListEnumeratorFactory].to[ListEnumeratorFactoryImpl].asEagerSingleton()
//
//    bind(classOf[IOStreamFactory]).asEagerSingleton()
//
//    <bean id="ioStreamFactory" class="com.banda.math.business.learning.IOStreamFactory">
//      <constructor-arg ref="functionEvaluatorFactory"/>
//    </bean>
//
//      <bean id="functionEvaluatorFactory" class="com.banda.function.business.FunctionEvaluatorFactoryImpl">
//        <constructor-arg ref="listEnumeratorFactory"/>
//      </bean>
//
//

    val applicationContext = new ClassPathXmlApplicationContext("math-conf.xml")
    SpringIntegration.bindAll(binder(), applicationContext)

//

  }
}