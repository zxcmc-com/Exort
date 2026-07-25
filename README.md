<p align="center">
  <a href="https://exort.zxcmc.com/">
    <img src="https://exort.zxcmc.com/images/github/logo-github.png" alt="Exort Logo">
  </a>
</p>

# Exort Storage Network

Exort adds a fully searchable storage network to your server. Items live in a centralized storage database and are accessed through terminals, monitors, and automation buses — all seamlessly implemented as a Paper plugin.

With stunning visuals, immersive interfaces, and a gameplay experience that feels like a fully featured mod, Exort brings advanced storage automation to vanilla Minecraft — no mods, client installations, or client-side setup required.

## Features
- **Storage Network**: central Storage block + cables + terminals.
- **Search & Sorting**: quick item search and category sorting in the terminal GUI.
- **Crafting Terminal**: craft directly from network items.
- **Monitors**: show storage fill % or live item counts.
- **Automation**: import/export buses with filters and modes.
- **Relays**: connect remote sections of the storage network.
- **Wireless Terminal**: access the network within range using a charged item.
- **Chunk Loaders**: keep chunks loaded, with always-on, owner-bound, and dormant variants for safer automation while players are away.

## Compatibility
- **Server software**: Paper / Purpur
- **Supported Minecraft**: 1.21.11-26.2
- **Java**: 25 for Minecraft 26.1+ servers; Java 21 remains supported for the generated plugin bytecode and older 1.21.11 servers.

## Installation
1) Build: `./gradlew build`
2) Put the jar into `plugins/` and start the server.

## Developer API

Starting with Exort 0.19.14, integrations can use an experimental read-only API v1 to identify
Exort items, blocks, and configured Storage tiers without accessing internal PDC keys, SQLite data,
or implementation classes.

Build the dedicated compile-only artifacts:

```bash
./gradlew apiJar apiSourcesJar apiJavadocJar
```

The outputs are written to `build/libs/` as `Exort-<version>-api.jar`,
`Exort-<version>-api-sources.jar`, and `Exort-<version>-api-javadoc.jar`. HTML Javadoc is generated
under `build/docs/exort-api/`.

Add the official API JAR as a compile-only dependency. Do not shade or bundle Exort API classes
inside your plugin:

```groovy
dependencies {
    compileOnly files('libs/Exort-0.19.14-api.jar')
    compileOnly 'io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT'
}
```

Declare Exort as a Paper server dependency:

```yaml
dependencies:
  server:
    Exort:
      load: BEFORE
      required: true
      join-classpath: true
```

Obtain the API through Bukkit's `ServicesManager` after Exort has enabled:

```java
import com.zxcmc.exort.api.ExortApi;

ExortApi exort = getServer().getServicesManager().load(ExortApi.class);
if (exort == null || exort.getApiVersion() != 1) {
    return;
}
```

Typed inspection exposes a stable content type, an optional Storage/Booster tier variant, and the
minimum safe item copy policy:

```java
exort.inspectItem(itemStack).ifPresent(descriptor -> {
    switch (descriptor.copyPolicy()) {
        case TEMPLATE -> handleTemplate(descriptor.type());
        case PRESERVE_STATE -> moveExactStack(itemStack);
        case PRESERVE_UNIQUE_IDENTITY -> moveWithoutCloningOrMerging(itemStack);
    }
});

exort.inspectBlock(block).ifPresent(descriptor -> {
    if (descriptor.chorusCarrier()) {
        handleExortChorusCarrier(descriptor.type());
    }
});
```

`inspectItem`, `inspectBlock`, `isExortBlock`, and `isExortChorusCarrier` must run on the primary
server thread and fail fast when called asynchronously. Returned descriptors are immutable and may
be retained for asynchronous processing. Storage tier getters are thread-safe.

Inspection is classification, not authorization: it does not grant ownership, protection access,
permission to move or copy an item, or permission to mutate Storage contents. Raw persistent IDs,
owners, charge, links, and marker state are intentionally not exposed. API v1 remains experimental
until Exort 1.0; breaking contract changes increment `getApiVersion()`.

The project license prohibits third parties from redistributing Exort binary artifacts, including
the API JAR, without written permission from the copyright holder. A plugin that only uses the API
as `compileOnly` and does not contain Exort source or binaries remains an independent extension.

## License
This project uses a source‑available license. See `LICENSE.md` for details.
