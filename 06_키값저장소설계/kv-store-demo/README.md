# Distributed Key-Value Store Demo

Spring Boot demo for chapter 6 key-value store synchronization.

The demo runs ten independent nodes:

- `A`: http://localhost:7906
- `B`: http://localhost:7916
- `C`: http://localhost:7926
- `D`: http://localhost:7936
- `E`: http://localhost:7946
- `F`: http://localhost:7956
- `G`: http://localhost:7966
- `H`: http://localhost:7976
- `I`: http://localhost:7986
- `J`: http://localhost:7996

Each key is mapped onto a SHA-256 consistent hash ring with virtual nodes. The default ring uses 16 virtual nodes per physical node, so the ten-node cluster has 160 ring tokens.

Default quorum settings:

```text
servers = 10
N = 5 replicas per key
W = 3
R = 2
```

`N` means the number of nodes that store a specific key, not the total server count. Because `W + R = N`, the read quorum and write quorum do not always overlap. That makes stale reads possible.

You can change `W` and `R` from the UI. Presets are provided for common trade-offs:

- `W=3, R=2`: stale reads are possible
- `W=3, R=3`: read/write quorums overlap
- `W=5, R=1`: read optimized, write requires every replica for that key
- `W=1, R=5`: write optimized, read checks every replica for that key

## Run

```bash
./gradlew bootJar
docker compose up --build
```

Open http://localhost:7906.

## Stale Read Scenario

1. Keep all nodes available and use key `cart:42`.
2. The UI shows the five replica owners for the selected key. Note the first three and last two owners.
3. `PUT cart:42=book` through any coordinator.
4. Pause the last two replica owner nodes shown in the placement row.
5. Change the value to `book,pen` and `PUT` through any available coordinator.
6. The write can reach quorum with the first three owners; the paused owners miss the new version.
7. Resume the paused owners.
8. Run `GET cart:42`. With `R=2`, a read that checks only stale owners can return the old value.

Then switch the preset to `W=3, R=3` and repeat the read. Since `W + R > N`, the read quorum overlaps the successful write quorum.

## Hinted Handoff Scenario

1. Keep `W=3, R=2`.
2. Use the placement row to identify the replica owners for `cart:42`.
3. Pause two replica owner nodes.
4. `PUT cart:42=book,pen` through any available coordinator.
5. The coordinator stores pending hints for unavailable replica owners.
6. Resume the paused nodes.
7. Open the `Data Sync` tab and watch the pending hints.
8. The coordinator retries hinted handoff automatically, so the missed replicas catch up after a short delay.
9. You can also run `Hinted Handoff` manually from the tab to force an immediate retry.

## Local Process Run

Running all ten nodes locally is verbose. Docker Compose is the recommended path. For local debugging, start one process per node with the same `NODE_ID`, `SERVER_PORT`, `PEERS`, `REPLICATION_FACTOR=5`, `VIRTUAL_NODES=16`, `WRITE_QUORUM=3`, and `READ_QUORUM=2` values shown in `docker-compose.yml`.
