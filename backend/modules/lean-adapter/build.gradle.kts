plugins {
  kotlin("jvm")
}

dependencies {
  implementation(project(":modules:domain"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
