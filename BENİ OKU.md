# Distributed Disk Register System

A distributed key-value storage system with automatic replication, multiple I/O modes, and fault tolerance.

---

## Features

| Feature | Description |
|---------|-------------|
| **SET/GET Protocol** | `SET <key> <value>` stores data, `GET <key>` retrieves it |
| **Automatic Replication** | Data is automatically copied to multiple nodes |
| **3 I/O Modes** | CLASSIC, UNBUFFERED, MEMORY_MAPPED for different performance needs |
| **Fault Tolerance** | If a node fails, data is retrieved from other nodes |
| **Auto Discovery** | New nodes automatically find and join the cluster |
| **Health Monitoring** | Nodes are checked every 10 seconds, failed nodes are removed |

---

## System Architecture

```
┌──────────────────┐
│  HaToKuSe Client │  Sends SET/GET commands
└────────┬─────────┘
         │ TCP (port 6666)
         ▼
┌──────────────────────────────────────────────┐
│           LEADER NODE (port 5555)            │
│  • TCP Server: Receives client requests      │
│  • DiskIO: Writes to disk using selected mode│
│  • gRPC: Communicates with other nodes       │
│  • Replication: Copies data to followers     │
└────────┬─────────────────────────────────────┘
         │ gRPC (Protobuf)
    ┌────┴────┐
    ▼         ▼
┌────────┐ ┌────────┐
│FOLLOWER│ │FOLLOWER│  Each writes to own disk
│ (5556) │ │ (5557) │  Serves GET requests
└────────┘ └────────┘
```

---

## I/O Modes

| Mode | Method | Avg. Time | Use Case |
|------|--------|-----------|----------|
| **CLASSIC** | BufferedWriter | ~450 us | General purpose (default) |
| **UNBUFFERED** | FileOutputStream + sync() | ~950 us | Maximum data safety |
| **MEMORY_MAPPED** | MappedByteBuffer (zero-copy) | ~1600 us | Large files |

### How They Differ

- **CLASSIC**: Writes to buffer first, flushes to disk when full
- **UNBUFFERED**: Every write is immediately synced to disk
- **MEMORY_MAPPED**: File is mapped directly to memory, bypasses kernel buffer

---

## Data Flow

### SET Command
```
1. Client → Leader: SET 42 ISTANBUL
2. Leader: Write to memory (ConcurrentHashMap)
3. Leader: Write to disk (data_5555/42.msg)
4. Leader → Followers: Replicate via gRPC
5. Followers: Write to memory + disk
6. Leader → Client: OK
```

### GET Command
```
1. Client → Leader: GET 42
2. Leader: Search in memory
3. If not found → Read from disk
4. If not on disk → Query other nodes via gRPC
5. Leader → Client: OK ISTANBUL
```

---

## Project Structure

| File | Purpose |
|------|---------|
| `NodeMain.java` | Main entry, TCP server, command processing, statistics |
| `DiskIO.java` | Implementation of 3 I/O modes |
| `FamilyServiceImpl.java` | gRPC service methods, replication handling |
| `NodeRegistry.java` | Node list management |
| `family.proto` | gRPC protocol definitions |

---

## Build & Run

### Tek Komutla Başlatma (Önerilen)
```powershell
cd "c:\Users\cooll\Desktop\sistem programlama_cool"
.\baslat.bat
```

### Farklı Bilgisayarlarda Çalıştırma

**1. Bilgisayar (İlk açan LEADER olur):**
```powershell
.\baslat.bat
```

**2. Bilgisayar (Otomatik FOLLOWER olur):**
- Aynı klasörü kopyalayın
- `baslat.bat` çalıştırın
- Otomatik olarak Leader'ı bulup bağlanacak

> **Not:** Tüm bilgisayarlar aynı ağda (WiFi/LAN) olmalıdır.

### Manuel Başlatma
```powershell
cd "c:\Users\cooll\Desktop\sistem programlama_cool\distributed-disk-register"
.\mvnw.cmd clean compile
.\mvnw.cmd exec:java "-Dexec.mainClass=com.example.family.NodeMain" "-Dexec.args=--mode=CLASSIC --tolerance=1"
```

### Manuel Lider Belirtme (Opsiyonel)
```powershell
.\mvnw.cmd exec:java "-Dexec.mainClass=com.example.family.NodeMain" "-Dexec.args=--leader=192.168.1.100"
```

### Test with HaToKuSe Client
```powershell
cd "c:\Users\cooll\Desktop\HaToKuSe-Client-main\hatokuse-client"
java HaToKuSeClient --host=<LEADER_IP> --durationMinutes=30
```

---

## Command Line Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--mode=` | CLASSIC | I/O mode selection |
| `--tolerance=` | 1 | Replication factor (N+1 total copies) |

---

## Node Roles

- **First node started** automatically becomes the **LEADER** (port 5555)
- All subsequent nodes become **FOLLOWER** nodes
- Leader handles client TCP connections on port 6666
- All nodes communicate via gRPC

---

## Replication

With `--tolerance=1`:
- Data is stored on 2 nodes (Leader + 1 Follower)

With `--tolerance=2`:
- Data is stored on 3 nodes (Leader + 2 Followers)

---

## Technologies Used

| Component | Technology |
|-----------|------------|
| Inter-node Communication | gRPC + Protocol Buffers |
| Client-Server | TCP Socket |
| Disk I/O | BufferedIO / Direct / NIO MappedByteBuffer |
| Memory Store | ConcurrentHashMap |
| Build System | Maven |

---

## Sample Output

```
==========================================
  DISTRIBUTED SYSTEM - COOL VERSION v2.0
  HaToKuSe Compatible + Replication + I/O Modes
==========================================
Node: 127.0.0.1:5555
I/O Mode: CLASSIC
Tolerance: 1 (data copied to 2 nodes)
Data Directory: data_5555
Started: 2026-01-08 18:16:35
Role: LEADER
------------------------------------------
TCP Listening: 127.0.0.1:6666

+------------------------------------------+
|         PERFORMANCE STATISTICS           |
+------------------------------------------+
| Node: 127.0.0.1:5555                     |
| I/O Mode: CLASSIC                        |
| Total SET: 1542                          |
| Total GET: 389                           |
| Records in Memory: 50                    |
| Successful Replications: 1542            |
| Avg Write Time: 423 us                   |
| Avg Read Time: 2 us                      |
| Active Nodes: 3                          |
|   - 127.0.0.1:5555  (ME)                 |
|   - 127.0.0.1:5556                       |
|   - 127.0.0.1:5557                       |
+------------------------------------------+
```

---

*System Programming - Final Project - January 2026*
