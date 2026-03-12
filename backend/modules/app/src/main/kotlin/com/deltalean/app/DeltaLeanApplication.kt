package com.deltalean.app

import com.deltalean.transport.api.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import java.nio.file.NoSuchFileException

fun Application.deltaLeanApplication(
  service: WorkspaceSessionService = WorkspaceSessionService()
) {

  install(ContentNegotiation) {
    json()
  }

  install(StatusPages) {
    exception<IllegalArgumentException> { call, cause ->
      call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
    }
    exception<NoSuchFileException> { call, cause ->
      call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "File not found"))
    }
    exception<NoSuchElementException> { call, cause ->
      call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
    }
    exception<IllegalStateException> { call, cause ->
      call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid state"))
    }
    exception<Throwable> { call, cause ->
      call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Internal server error"))
    }
  }

  routing {
    registerWorkspaceRoutes(service)
  }
}
