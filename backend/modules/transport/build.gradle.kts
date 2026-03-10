plugins {
  kotlin("jvm")
  id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

dependencies {
  implementation(project(":modules:domain"))
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
