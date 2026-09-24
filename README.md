# Sparrow-KV

A distributed in memory key-value store built from scratch in Java, supporting concurrent
clients, TTL expiration, leader/follower replication, and automated failure detection with
self-promotion. 

This project was created to learn more about **networking, concurrency, and distributed systems** without relying on frameworks.

## Features

* Custom TCP server using Java `ServerSocket` and `Socket`
* Supports multiple clients using **Java 21 virtual threads**
* Commands: `SET`, `GET`, `DEL`, `EXPIRE`, `PING`
* **TTL (time-to-live) expiration**:  both passive (checked on `GET`) and active (background sweep every second)
* **Leader-follower replication**: writes on the leader are automatically forwarded to every
  configured follower 
* **Heartbeat-based failure detection**: followers periodically ping the leader; after 3
  consecutive missed heartbeats, a follower self-promotes (two-node case)
* **Load-tested**: 70 concurrent clients sustained, ~13,000 ops/sec, zero failures
## Architecture

```text
                 ┌─────────────┐
     writes ───► │   LEADER    │
                 │  (port A)   │
                 └──────┬──────┘
                        │ forwards SET/DEL/EXPIRE
             ┌──────────┴──────────┐
             ▼                     ▼
      ┌─────────────┐       ┌─────────────┐
      │  FOLLOWER   │       │  FOLLOWER   │
      │  (port B)   │       │  (port C)   │
      └─────────────┘       └─────────────┘
             │
             │ heartbeat (PING/PONG, every 1.5s)
             ▼
      if leader unreachable for 3 consecutive
      heartbeats → follower self-promotes
```

The leader handles writes and forwards them to the followers.

If a follower loses connection to the leader, it can automatically promote itself to leader in the two-node setup.

## Getting Started

**Clone the repo:**
```
git clone https://github.com/Gbemi-MDoye/sparrow-kv.git
cd sparrow-kv
```

### Requirements

* Java 21+
* Maven

### Build

```bash
mvn compile
```

### Run a single server

```bash
java -cp target/classes org.example.Server 6379
```

### Run a cluster

Start the leader:

```bash
java -cp target/classes org.example.Server 6379 LEADER 6380 6381
```

Start the followers:

```bash
java -cp target/classes org.example.Server 6380 FOLLOWER 6379
java -cp target/classes org.example.Server 6381 FOLLOWER 6379
```

## Commands

```text
SET name Gbemi
GET name
EXPIRE name 30
DEL name
PING
```


| Command              | Description                    |
| -------------------- | ------------------------------ |
| `SET key value`      | Store a value                  |
| `GET key`            | Retrieve a value               |
| `DEL key`            | Delete a value                 |
| `EXPIRE key seconds` | Set an expiration time         |
| `PING`               | Check if the server is running |

## Benchmark

The project includes a simple load tester:

```bash
java -cp target/classes org.example.LoadTest 6379 70 50
```

Test result:

* 70 concurrent clients, 50 commands each. 


Results on this machine: 
* **70 concurrent clients,
3,500/3,500 successful, ~13,000 ops/sec.** Beyond ~70-80 simultaneous *new* connections,
failures appear due to OS-level TCP backlog limits, the server's backlog was
increased (`new ServerSocket(port, 200)`) which helped but didn't fully eliminate the ceiling;
noted as a known limitation below.

## Known Limitations

* Leader election currently works for the **two-node case**
* No automatic client service discovery
* Data is stored only in memory, so restarting the server loses data
* Very large connection bursts can hit the OS TCP backlog limit

## Tech Stack

* **Java 21**
* **Maven**
* **TCP Sockets**
* **Virtual Threads**
* **ConcurrentHashMap**
* **Distributed Systems concepts**
