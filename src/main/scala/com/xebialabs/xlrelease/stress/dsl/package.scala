package com.xebialabs.xlrelease.stress

import com.xebialabs.xlrelease.stress.dsl.exec.Control
import com.xebialabs.xlrelease.stress.dsl.xlr.Xlr
import freestyle.free.logging.LoggingM
import freestyle.free.{FreeS, module}

package object dsl {
  @module trait DSL {
    val control: Control
    val xlr: Xlr
    val log: LoggingM
  }

  type API = DSL[DSL.Op]
  type Program[A] = FreeS[DSL.Op, A]
}
