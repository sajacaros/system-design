# Distributed Key-Value Store Demo

Spring Boot demo for chapter 6 key-value store synchronization.

The demo runs five independent nodes:

- `A`: http://localhost:7906
- `B`: http://localhost:7916
- `C`: http://localhost:7926
- `D`: http://localhost:7936
- `E`: http://localhost:7946

Default quorum settings:

```text
N = 5
W = 3
R = 2
```

Because `W + R = N`, the read quorum and write quorum do not always overlap. That makes stale reads possible.

You can change `W` and `R` from the UI. Presets are provided for common trade-offs:

- `W=3, R=2`: stale reads are possible
- `W=3, R=3`: read/write quorums overlap
- `W=5, R=1`: read optimized, write requires every replica
- `W=1, R=5`: write optimized, read checks every replica

## Run

```bash
./gradlew bootJar
docker compose up --build
```

Open http://localhost:7906.

## Stale Read Scenario

1. Keep all nodes available and `PUT cart:42=book` via node `A`.
2. Pause nodes `D` and `E`.
3. Change the value to `book,pen` and `PUT` via node `A`.
4. The write reaches quorum with `A`, `B`, and `C`; `D` and `E` miss the new version.
5. Resume `D` and `E`.
6. Select target node `D` and run `GET cart:42`.
7. Node `D` reads only `R=2` replicas: itself and `E`. Both can still have the old value, so the response can be stale.

Then switch the preset to `W=3, R=3` and repeat the read. Since `W + R > N`, the read quorum overlaps the successful write quorum.

## Local Process Run

Use five terminals:

```bash
NODE_ID=A SERVER_PORT=7906 PEERS=B=http://localhost:7916,C=http://localhost:7926,D=http://localhost:7936,E=http://localhost:7946 REPLICATION_FACTOR=5 WRITE_QUORUM=3 READ_QUORUM=2 ./gradlew bootRun
NODE_ID=B SERVER_PORT=7916 PEERS=A=http://localhost:7906,C=http://localhost:7926,D=http://localhost:7936,E=http://localhost:7946 REPLICATION_FACTOR=5 WRITE_QUORUM=3 READ_QUORUM=2 ./gradlew bootRun
NODE_ID=C SERVER_PORT=7926 PEERS=A=http://localhost:7906,B=http://localhost:7916,D=http://localhost:7936,E=http://localhost:7946 REPLICATION_FACTOR=5 WRITE_QUORUM=3 READ_QUORUM=2 ./gradlew bootRun
NODE_ID=D SERVER_PORT=7936 PEERS=E=http://localhost:7946,A=http://localhost:7906,B=http://localhost:7916,C=http://localhost:7926 REPLICATION_FACTOR=5 WRITE_QUORUM=3 READ_QUORUM=2 ./gradlew bootRun
NODE_ID=E SERVER_PORT=7946 PEERS=D=http://localhost:7936,A=http://localhost:7906,B=http://localhost:7916,C=http://localhost:7926 REPLICATION_FACTOR=5 WRITE_QUORUM=3 READ_QUORUM=2 ./gradlew bootRun
```
