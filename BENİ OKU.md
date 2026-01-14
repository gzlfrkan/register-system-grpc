# Distributed Disk Register System

A distributed key-value storage system with automatic replication, intelligent load balancing, and fault tolerance.

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
| **Leader Coordination** | Leader does NOT store files, only manages replication |
| **Location Tracking** | Leader tracks which data is stored on which followers |
| **Least-Loaded Distribution** | New data goes to followers with least storage usage |

---

## System Architecture
```
┌──────────────────┐
│  HaToKuSe Client │  Sends SET/GET commands
└────────┬─────────┘
         │ TCP (port 6666)
         ▼
┌──────────────────────────────────────────────────────┐
│              LEADER NODE (port 5555)                 │
│  ┌─────────────────────────────────────────────────┐ │
│  │ • NO local storage (coordination only)         │ │
│  │ • veriKonumlari: tracks data locations         │ │
│  │ • followerBoyutlari: caches follower sizes     │ │
│  │ • Least-Loaded selection for new data          │ │
│  └─────────────────────────────────────────────────┘ │
└────────┬─────────────────────────────────────────────┘
         │ gRPC (Protobuf)
    ┌────┴────┬────────┐
    ▼         ▼        ▼
┌────────┐ ┌────────┐ ┌────────┐
│FOLLOWER│ │FOLLOWER│ │FOLLOWER│  Each writes to own disk
│ (5556) │ │ (5557) │ │ (5558) │  Reports storage size
│ 12.5KB │ │  8.3KB │ │ 15.1KB │  to Leader (cached)
└────────┘ └────────┘ └────────┘
```

---

## I/O Modes

| Mode | Method | Avg. Time | Use Case |
|------|--------|-----------|----------|
| **CLASSIC** | BufferedWriter | ~450 us | General purpose (default) |
| **UNBUFFERED** | FileOutputStream + sync() | ~950 us | Maximum data safety |
| **MEMORY_MAPPED** | MappedByteBuffer (zero-copy) | ~1600 us | Large files |

---

## Data Flow

### SET Command
```
1. Client → Leader: SET 42 ISTANBUL
2. Leader: Query followerBoyutlari cache
3. Leader: Select least-loaded 'tolerance' followers
4. Leader → Selected Followers: Replicate via gRPC
5. Followers: Write to memory + disk
6. Leader: Update veriKonumlari[42] = [follower1, follower2]
7. Leader → Client: OK
```

### GET Command
```
1. Client → Leader: GET 42
2. Leader: Check veriKonumlari[42] for known locations
3. Leader → Known Followers: Query directly (no broadcast)
4. If not found → Query all followers
5. Leader → Client: OK ISTANBUL
```

---

## Least-Loaded Distribution

```
┌─────────────────────────────────────────────────────┐
│  Leader every 5 seconds:                            │
│  1. Query all followers: GetStorageInfo RPC         │
│  2. Cache their total bytes                         │
│                                                     │
│  When SET arrives:                                  │
│  1. Sort followers by storage size                  │
│  2. Select 'tolerance' least-loaded ones            │
│  3. Replicate to those followers                    │
└─────────────────────────────────────────────────────┘
```

---

## Project Structure

| File | Purpose |
|------|---------|
| `NodeMain.java` | Main entry, TCP server, command processing, statistics, load balancing |
| `DiskIO.java` | Implementation of 3 I/O modes + file count/size methods |
| `FamilyServiceImpl.java` | gRPC service methods, replication handling, GetStorageInfo |
| `NodeRegistry.java` | Node list management |
| `family.proto` | gRPC protocol definitions + StorageInfo message |

---

## Build & Run

### Tek Komutla Başlatma (Önerilen)
```powershell
cd "c:\Users\cooll\Desktop\sistem programlama_cool\çalıştırma"
.\baslat.bat          # Leader başlatır
.\yeni_uye.bat        # Follower başlatır (birden fazla çalıştırılabilir)
.\komut_gonder.bat    # SET/GET komutları gönderir
```

### Command Line Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--mode=` | CLASSIC | I/O mode: CLASSIC, UNBUFFERED, MEMORY_MAPPED |
| `--tolerance=` | 2 | Replication factor (data stored on N followers) |
| `--leader=` | auto | Manual leader IP specification |

---

## Sample Output

### Leader Statistics
```
+------------------------------------------+
|         PERFORMANCE STATISTICS           |
+------------------------------------------+
| Node: 192.168.1.5:5555                   |
| I/O Mode: CLASSIC                        |
| Time: 2026-01-14 15:45:00                |
+------------------------------------------+
| Total SET: 500                           |
| Total GET: 125                           |
| Records in Memory: 0                     |
| Successful Replications: 1000            |
+------------------------------------------+
| Active Nodes: 3                          |
|   - 192.168.1.5:5555 (LEADER)            |
|   - 192.168.1.5:5556                     |
|   - 192.168.1.5:5557                     |
+------------------------------------------+
| FOLLOWER STORAGE (Cache):                |
|   - 192.168.1.5:5556        12.50 KB     |
|   - 192.168.1.5:5557         8.30 KB     |
+------------------------------------------+
```

### Follower Statistics
```
+------------------------------------------+
|       FOLLOWER STORAGE STATISTICS        |
+------------------------------------------+
| Node: 192.168.1.5:5556                   |
| Data Dir: data_192_168_1_5_5556          |
+------------------------------------------+
| Files on Disk: 250                       |
| Total Size: 12.50 KB                     |
| Records in Memory: 250                   |
+------------------------------------------+
| SET Received: 250                        |
| GET Received: 50                         |
+------------------------------------------+
```

---

## Technologies Used

| Component | Technology |
|-----------|------------|
| Inter-node Communication | gRPC + Protocol Buffers |
| Client-Server | TCP Socket |
| Disk I/O | BufferedIO / Direct / NIO MappedByteBuffer |
| Memory Store | ConcurrentHashMap |
| Load Balancing | Size-based Least-Loaded Selection |
| Build System | Maven |

---

*System Programming - Final Project - January 2026*
