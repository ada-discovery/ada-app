package services.ml.transformers

import java.{lang => jl}

import com.banda.network.business.NetworkRunnableFactoryUtil.NetworkRunnable
import com.banda.network.domain.TopologicalNode

protected class NetworkStateVectorAgg(
  networkRunnable: NetworkRunnable[jl.Double],
  inputNodes: Seq[TopologicalNode],
  outputNodes: Seq[TopologicalNode]
) extends StateFunctionVectorAgg {

  override protected def newStateFun(
    inputWithIndeces: Traversable[(Double, Int)]
  ): Array[Double] = {
    println("Network Runnable Time     : " + networkRunnable.currentTime)
    println("Network Runnable HashCode : " + networkRunnable.hashCode())
    println("Input Nodes HashCode      : " + inputNodes.map(_.hashCode).mkString(", "))
    println("Output Nodes HashCode     : " + outputNodes.map(_.hashCode).mkString(", "))
    println("-------------")

    println("Input Nodes Indeces       : " + inputNodes.map(_.getIndex).mkString(", "))
    println("Output Nodes Indeces      : " + outputNodes.map(_.getIndex).mkString(", "))

    // set the inputs
    inputWithIndeces.foreach { case (input, index) =>
      val inputNode = inputNodes(index)
      networkRunnable.setState(inputNode, input)
    }

    // run for one step
    networkRunnable.runFor(1)

    // get the output states
    outputNodes.map(outputNode => networkRunnable.getState(outputNode): Double).toArray
  }
}