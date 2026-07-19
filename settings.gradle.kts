@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "proxytransport-geyser"

// This extension builds against the modified Geyser core that carries the transport SPI. Because Geyser's
// full build pulls in the Fabric/NeoForge mod modules (which require a newer JDK than this extension targets),
// we don't use a composite build. Instead, publish the modified core to your local Maven repository first:
//
//     cd ../Geyser && ./gradlew :core:publishToMavenLocal :api:publishToMavenLocal :common:publishToMavenLocal
//
// (run with a Java 21+ JDK). The dependencies in build.gradle.kts then resolve from mavenLocal().

// Shared ProxyTransport wire implementation, vendored as a git submodule (see .gitmodules).
// Run `git submodule update --init` after cloning.
include(":common")
project(":common").projectDir = file("common")
