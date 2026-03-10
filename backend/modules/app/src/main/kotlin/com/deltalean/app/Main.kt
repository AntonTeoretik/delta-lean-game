package com.deltalean.app

import com.deltalean.transport.api.ErrorResponse
import com.deltalean.transport.api.FileContentResponse
import com.deltalean.transport.api.FileUpdateRequest
import com.deltalean.transport.api.FilesListResponse
import com.deltalean.transport.api.OpenWorkspaceRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.nio.file.NoSuchFileException

fun main() {
  val port = System.getProperty("app.port")?.toIntOrNull()
    ?: System.getenv("PORT")?.toIntOrNull()
    ?: 8081

  embeddedServer(Netty, port = port) {
    deltaLeanApplication()
  }.start(wait = true)
}

fun Application.deltaLeanApplication() {
  val service = WorkspaceSessionService()

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
    exception<IllegalStateException> { call, cause ->
      call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid state"))
    }
    exception<Throwable> { call, cause ->
      call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Internal server error"))
    }
  }

  routing {
    post("/api/workspace/open") {
      val request = call.receive<OpenWorkspaceRequest>()
      val response = service.openWorkspace(request.rootPath)
      call.respond(response)
    }

    get("/api/files") {
      call.respond(FilesListResponse(service.listFiles()))
    }

    route("/api/files/{path...}") {
      get {
        val path = requiredPath(call.parameters.getAll("path"))
        val content = service.readFile(path)
        call.respond(FileContentResponse(path = path, content = content))
      }

      put {
        val path = requiredPath(call.parameters.getAll("path"))
        val request = call.receive<FileUpdateRequest>()
        service.updateFile(path, request.content)
        call.respond(HttpStatusCode.OK)
      }
    }

    get("/api/diagnostics") {
      val path = call.request.queryParameters["path"]
      call.respond(service.getDiagnostics(path))
    }
  }
}

private fun requiredPath(pathSegments: List<String>?): String {
  require(!pathSegments.isNullOrEmpty()) { "Missing file path" }
  return pathSegments.joinToString("/")
}
