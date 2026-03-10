package com.deltalean.app

import com.deltalean.transport.api.FileContentResponse
import com.deltalean.transport.api.FileUpdateRequest
import com.deltalean.transport.api.FilesListResponse
import com.deltalean.transport.api.OpenWorkspaceRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.registerWorkspaceRoutes(service: WorkspaceSessionService) {
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

private fun requiredPath(pathSegments: List<String>?): String {
  require(!pathSegments.isNullOrEmpty()) { "Missing file path" }
  return pathSegments.joinToString("/")
}
