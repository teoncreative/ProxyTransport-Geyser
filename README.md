# ProxyTransport for Geyser

A [Geyser](https://github.com/GeyserMC/Geyser) extension that adds support for the NetherGames
**[ProxyTransport](https://github.com/NetherGamesMC/ProxyTransport)** protocol — a raw **TCP / QUIC** transport
that replaces the inefficient RakNet link between a Bedrock proxy and its downstream servers.

It is the Geyser-side counterpart to
[ProxyTransport-PM](https://github.com/NetherGamesMC/ProxyTransport-PM) (the PocketMine downstream plugin):
this extension makes **Geyser act as a ProxyTransport downstream server**, so a WaterdogPE proxy running the
ProxyTransport plugin can connect to Geyser over TCP/QUIC instead of RakNet.

```
Bedrock client ──RakNet──▶ WaterdogPE (+ProxyTransport plugin) ──TCP/QUIC──▶ Geyser (+this extension) ──▶ Java server
```

## How it works

- Registers a `GeyserBedrockTransport` (TCP and/or QUIC) during `GeyserPreInitializeEvent`, before Geyser binds.
- Each accepted TCP socket / QUIC bidirectional stream runs the ProxyTransport pipeline
  (`[length][compression-byte][batch]`, with the Zstd `254`/`-2` extension) and becomes a normal Geyser session.
- **No Bedrock encryption** is negotiated — ProxyTransport links are trusted, and the proxy forwards the
  player's already-authenticated identity (xuid + IP), exactly like WaterdogPE forwarding.
- Answers the proxy's `NetworkStackLatencyPacket` probes so the proxy can measure ping; over QUIC, latency is
  also reported back to Geyser from QUIC path stats.

### A note on QUIC and classloaders

Netty loads the QUIC native library through `netty-common` and resolves the native's JNI helper classes via
**whichever classloader loaded `netty-common`** (that's the classloader `System.load` runs under). If the QUIC
classes only lived in the extension's *child* classloader, the native load fails (`NoClassDefFoundError`). So
the QUIC jars are **not** shaded into the extension — they're embedded as resources and, at startup, injected
directly onto netty-common's classloader (`QuicLibraryInstaller`). On Paper this is a separate library
classloader, *not* Geyser's own — the installer locates it via a netty class. TCP needs none of this (zstd-jni's
loader and classes share the extension jar, so it loads fine).

## Building

```bash
cd ../ProxyTransportGeyser
./gradlew build
```

The extension jar is produced at `build/libs/ProxyTransport-Geyser.jar`. Drop it into Geyser's `extensions/` folder.

## Configuration (`extensions/proxytransport/config.yml`)

```yaml
address: 0.0.0.0
tcp:
  enabled: true
  port: 19132
quic:
  enabled: false      # requires the native QUIC library (bundled for linux-x86_64)
  port: 19133
disable-raknet: true  # don't also expose Geyser's RakNet listener
```

## Requirements & caveats

- **QUIC requires a JVM flag.** On Java 17+ (which modern Paper/Spigot needs), injecting the QUIC jars onto
  Geyser's classloader uses `URLClassLoader#addURL` reflectively, which is blocked by module encapsulation.
  Add this to your server start command and restart:
  ```
  --add-opens java.base/java.net=ALL-UNNAMED
  ```
  Without it, QUIC is skipped with a clear log message (TCP/RakNet are unaffected). TCP needs no flags.
- **QUIC** uses `netty-codec-native-quic`; only the **linux-x86_64** native is embedded. For other platforms,
  add the matching `io.netty:netty-codec-native-quic:<version>:<classifier>` native, or use TCP only.
- The proxy **must** forward the player's xuid and IP (WaterdogPE forwarding). Because the link is unencrypted,
  Geyser relies on this for the real player identity. If it isn't forwarded, the connection is rejected.
- QUIC uses an ephemeral self-signed certificate with ALPN `ng`; the WaterdogPE client trusts any certificate,
  matching the upstream ProxyTransport design.

## License

The Geyser-facing code follows Geyser's MIT license; the protocol design mirrors NetherGames' ProxyTransport.
