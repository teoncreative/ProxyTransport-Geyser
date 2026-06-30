# ProxyTransport for Geyser

A [Geyser](https://github.com/GeyserMC/Geyser) extension adding the NetherGames
**[ProxyTransport](https://github.com/NetherGamesMC/ProxyTransport)** protocol — a raw **TCP / QUIC** transport
that replaces the RakNet link between a Bedrock proxy and its downstream servers.

It makes **Geyser act as a ProxyTransport downstream server**, so a WaterdogPE proxy running the ProxyTransport
plugin connects to Geyser over TCP/QUIC instead of RakNet. (It is the Geyser counterpart to the PocketMine
[ProxyTransport-PM](https://github.com/NetherGamesMC/ProxyTransport-PM) plugin.)

```
Bedrock client ──RakNet──▶ WaterdogPE (+ProxyTransport plugin) ──TCP/QUIC──▶ Geyser (+this extension) ──▶ Java server
```

## How it works

- On `GeyserDefineBedrockTransportsEvent`, registers a `GeyserBedrockTransport` for TCP and/or QUIC (and
  optionally removes the built-in RakNet transport).
- Each accepted TCP socket / QUIC stream runs the ProxyTransport pipeline (`[length][compression-byte][batch]`,
  with the Zstd `254`/`-2` extension) and becomes a normal Geyser session.
- **No Bedrock encryption** is negotiated — the link is trusted and the proxy forwards the player's
  already-authenticated identity (xuid + IP), like WaterdogPE forwarding.
- Answers the proxy's `NetworkStackLatencyPacket` probes; over QUIC, latency is reported back to Geyser from
  QUIC path stats.

### QUIC and classloaders

Netty loads the QUIC native through `netty-common` and resolves the native's JNI classes via the classloader
that loaded `netty-common`. So the QUIC jars are **not** shaded into the extension — they're embedded as
resources and injected onto that classloader at startup (`QuicLibraryInstaller`). TCP needs none of this.

## Releases

Tagged `v*` pushes publish a full release; every commit to `main` updates a rolling `latest` **prerelease**.
Drop `ProxyTransport-Geyser.jar` into Geyser's `extensions/` folder.

## Building

```bash
./gradlew build   # -> build/libs/ProxyTransport-Geyser.jar
```

The build resolves Geyser core/api from `mavenLocal()`; until the transport SPI is in a published Geyser
snapshot, build the Geyser branch first:

```bash
cd ../Geyser
./gradlew :core:publishToMavenLocal :api:publishToMavenLocal :common:publishToMavenLocal
```

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

## QUIC notes & caveats

- **QUIC needs a JVM flag** on Java 17+ (reflecting into a classloader is blocked by module encapsulation).
  Which flag depends on where netty lives:
  - Paper/Spigot (netty in a library classloader): `--add-opens java.base/java.net=ALL-UNNAMED`
  - Geyser Standalone (netty on the system classloader): `--add-opens java.base/jdk.internal.loader=ALL-UNNAMED`

  Without it, QUIC is skipped with a log line naming the flag to add; TCP/RakNet are unaffected.
- Only the **linux-x86_64** QUIC native is embedded. For other platforms, add the matching
  `io.netty:netty-codec-native-quic:<version>:<classifier>`, or use TCP.
- The proxy **must** forward the player's xuid and IP. Because the link is unencrypted, Geyser relies on this
  for the real identity; if it isn't forwarded, the connection is rejected.
- QUIC uses an ephemeral self-signed certificate with ALPN `ng`, which the WaterdogPE client trusts.

## License

MIT, following Geyser's license.
