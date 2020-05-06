package org.ada.server.services

import scala.concurrent.Future

trait InputExec[IN] extends (IN => Future[Unit])
