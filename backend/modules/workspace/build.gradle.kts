plugins {
  kotlin("jvm")
}

dependencies {
  implementation(project(":modules:domain"))

  testImplementation(kotlin("test-junit5"))
}

tasks.test {
  useJUnitPlatform()
}
