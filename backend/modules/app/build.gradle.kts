plugins {
  kotlin("jvm")
  application
}

dependencies {
  implementation(project(":modules:domain"))
  implementation(project(":modules:workspace"))
  implementation(project(":modules:lean-adapter"))
  implementation(project(":modules:transport"))

  implementation("io.ktor:ktor-server-core:2.3.7")
  implementation("io.ktor:ktor-server-netty:2.3.7")
  implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
  implementation("io.ktor:ktor-server-status-pages:2.3.7")
  implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
  implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
  mainClass.set("com.deltalean.app.MainKt")
}
