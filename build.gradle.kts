plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
}

val id = project.property("id") as String
// Note: read "extensionName", not "name" - Gradle's built-in project `name` property would shadow it.
val extensionName = project.property("extensionName") as String
val author = project.property("author") as String

// The Geyser version this extension is built and verified against.
val geyserVersion = "2.10.1-SNAPSHOT"
// The API version advertised in extension.yml (human.major.minor).
val geyserApiVersion = "2.10.1"

val nettyVersion = "4.2.7.Final"

repositories {
    // The modified Geyser core/api/common, published locally (see settings.gradle.kts).
    mavenLocal()
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    mavenCentral()
}

// QUIC jars are embedded (not shaded) and injected onto netty's classloader at runtime; see QuicLibraryInstaller.
val quicLibs by configurations.creating

dependencies {
    // Provided by Geyser (which transitively exposes Netty + the Bedrock protocol libraries).
    compileOnly("org.geysermc.geyser:core:$geyserVersion")
    compileOnly("org.geysermc.geyser:api:$geyserVersion")
    // RakNet is implementation-scoped in core; needed only to compile against GeyserBedrockPeer.
    compileOnly("org.cloudburstmc.netty:netty-transport-raknet:1.1.0.CR1-SNAPSHOT")

    // QUIC: needed at compile time only; embedded (not shaded) and injected onto Geyser's classloader at runtime.
    compileOnly("io.netty:netty-codec-classes-quic:$nettyVersion") { isTransitive = false }
    quicLibs("io.netty:netty-codec-classes-quic:$nettyVersion") { isTransitive = false }
    quicLibs("io.netty:netty-codec-native-quic:$nettyVersion:linux-x86_64") { isTransitive = false }

    // Bundled (shaded) into the extension jar - not provided by Geyser.
    implementation("com.github.luben:zstd-jni:1.5.5-4")
    // Used by our SelfSignedCertificateGenerator to mint the QUIC server certificate headlessly.
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    processResources {
        filesMatching("extension.yml") {
            expand(
                "id" to id,
                "name" to extensionName,
                "api" to geyserApiVersion,
                "version" to version,
                "author" to author
            )
        }
        // Embed the QUIC jars under quic-libs/ with stable names so the runtime installer can find them.
        from(quicLibs) {
            into("quic-libs")
            rename { fileName ->
                if (fileName.contains("native")) "netty-codec-native-quic.jar" else "netty-codec-classes-quic.jar"
            }
        }
    }

    shadowJar {
        archiveFileName.set("ProxyTransport-Geyser.jar")
        // Drop signature files from signed dependencies (e.g. BouncyCastle) so the shaded jar stays valid.
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    build {
        dependsOn(shadowJar)
    }
}
