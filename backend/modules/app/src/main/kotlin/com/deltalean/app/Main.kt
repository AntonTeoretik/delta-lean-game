package com.deltalean.app

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
  val port = System.getProperty("app.port")?.toIntOrNull()
    ?: System.getenv("PORT")?.toIntOrNull()
    ?: 8081

  embeddedServer(Netty, port = port) {
    deltaLeanApplication()
  }.start(wait = true)
}
