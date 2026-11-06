# SilentJoin
truly the best paper plugin to hide and customize the join, leave and name-change messages.

## Features
- Hides the yellow join, leave and name-change messages
- Optional custom messages with MiniMessage support
- On/off toggle and `/silentjoin reload`
- Velocity support: one jar for Paper, Folia and Velocity

## Supported platforms
- Paper 26.3
- Folia 26.3
- Velocity 4.2 or newer

## Velocity networks
Put the same jar on the proxy and on every backend server.

- The backend servers hide their own join, leave and name-change messages (default config).
- The proxy announces network joins, leaves and server switches to every player on the network.
  Configure these messages in `plugins/silentjoin/config.yml` on the proxy.

## Commands and permissions
 
| Command                   | Permission                | Default      | Description                        |
|---------------------------|---------------------------|--------------|------------------------------------|
| `/silentjoin reload`      | `silentjoin.reload`       | op           | Reloads `config.yml` on the server |
| `/silentjoinproxy reload` | `silentjoin.proxy.reload` | console only | Reloads `config.yml` on the proxy  |


## Building

After cloning this repository, build the project with Gradle by running `./gradlew build` and take the created jar out of the `build/libs` directory.

You need JDK 25 or newer to build SilentJoin.

## Releasing

1. Set the new `version` in `build.gradle.kts` and push the commit.
2. Push a matching tag, for example `git tag v1.1.0 && git push origin v1.1.0`.

GitHub Actions then builds the jar and publishes it as a GitHub release.
The release fails if the tag does not match the version in `build.gradle.kts`.
