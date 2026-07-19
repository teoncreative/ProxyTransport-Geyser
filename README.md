# ProxyTransport for Geyser

A [Geyser](https://github.com/GeyserMC/Geyser) extension that lets a WaterdogPE proxy running the
[ProxyTransport](https://github.com/teoncreative/ProxyTransport) plugin reach Geyser over **TCP or QUIC**
instead of RakNet.

```
Bedrock client ──RakNet──▶ WaterdogPE (+ProxyTransport) ──TCP/QUIC──▶ Geyser (+this extension) ──▶ Java server
```

> [!IMPORTANT]
> The Geyser-side support this extension needs is **not merged upstream yet**. Until it is, you need a Geyser
> build that includes [GeyserMC/Geyser#6488](https://github.com/GeyserMC/Geyser/pull/6488).

## Setup

1. Run a Geyser build that includes the PR above.
2. Drop `ProxyTransport-Geyser.jar` into Geyser's `extensions/` folder.
3. Start Geyser once to generate the config, edit it, then restart to apply.

## Configuration

`extensions/proxytransport/config.yml`:

```yaml
address: 0.0.0.0
tcp:
  enabled: true
  port: 19132
quic:
  enabled: false
  port: 19133
disable-raknet: true  # don't also expose Geyser's RakNet listener
```

Enable whichever transport your proxy is configured to use. Leave `disable-raknet: true` when only the proxy
connects to this Geyser instance.

## QUIC

QUIC needs an extra JVM flag on Java 17+, because the QUIC native has to be loaded into Netty's class loader:

```
--add-opens java.base/jdk.internal.loader=ALL-UNNAMED
```

If the flag is missing, QUIC is skipped.

Only the **linux-x86_64** QUIC native is bundled. On other platforms, use TCP.

The proxy must forward the player's xuid and IP. The link carries no Bedrock encryption, so Geyser relies on
that forwarding for the real identity; connections without it are rejected.

## License

MIT, following Geyser's license.