package dataaccess.mongo

import reactivemongo.api.commands.{Command, CommandWithResult, UnitBox}

case class FSyncCommand(
    async: Boolean = true,
    lock: Boolean = false
  ) extends Command with CommandWithResult[UnitBox.type]
