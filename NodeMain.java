package com.example.family;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Ana giriş noktası - Node başlatıcı.
 * Lider (5555) veya üye olarak çalışır.
 */
public class NodeMain {

    private static final String HOST = "127.0.0.1";
    private static final int BASE_PORT = 5555;
    private static final int TCP_PORT = 6666;
    private static final int MAX_PORT = 5565; // max 10 üye

    private final int grpcPort;
    private final NodeRegistry registry;
    private final DiskStorage diskStorage;
    private Server grpcServer;
    private TcpServer tcpServer;
    private int tolerance = 2;

    // Mesaj-üye eşlemesi (sadece lider tutar)
    private final Map<Integer, List<NodeRegistry.Member>> messageMap = new ConcurrentHashMap<>();

    // gRPC client'lar (üyelere bağlantı)
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public NodeMain(int grpcPort) {
        this.grpcPort = grpcPort;
        this.registry = new NodeRegistry(HOST, grpcPort);
        this.diskStorage = new DiskStorage("node_" + grpcPort);
        loadTolerance();
    }

    private void loadTolerance() {
        Path configPath = Paths.get("tolerance.conf");
        if (Files.exists(configPath)) {
            try {
                String content = Files.readString(configPath);
                for (String line : content.split("\n")) {
                    if (line.startsWith("TOLERANCE=")) {
                        tolerance = Integer.parseInt(line.substring(10).trim());
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("tolerance.conf okunamadi: " + e.getMessage());
            }
        }
        System.out.println("Tolerance: " + tolerance);
    }

    public void start() throws Exception {
        // gRPC server başlat
        grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(new FamilyServiceImpl(registry, diskStorage))
                .addService(new StorageServiceImpl(diskStorage))
                .build()
                .start();

        System.out.println("Node started on " + HOST + ":" + grpcPort);

        if (registry.isLeader()) {
            // Lider: TCP server başlat
            tcpServer = new TcpServer(TCP_PORT, this);
            tcpServer.start();
            System.out.println("Leader listening for text on TCP " + HOST + ":" + TCP_PORT);
        } else {
            // Üye: Lidere ve diğer üyelere katıl
            joinFamily();
        }

        // Periyodik aile listesi basımı
        startFamilyPrinter();

        // Periyodik health check
        startHealthChecker();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        // Bekle
        grpcServer.awaitTermination();
    }

    public void stop() {
        System.out.println("Node kapaniyor...");
        if (tcpServer != null) {
            tcpServer.stop();
        }
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
        for (ManagedChannel channel : channels.values()) {
            channel.shutdown();
        }
    }

    private void joinFamily() {
        System.out.println("Aileye katiliniyor...");

        // 5555'ten kendi portuna kadar tüm üyelere Join gönder
        for (int port = BASE_PORT; port < grpcPort; port++) {
            try {
                ManagedChannel channel = getChannel(HOST, port);
                FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);

                JoinRequest request = JoinRequest.newBuilder()
                        .setHost(HOST)
                        .setPort(grpcPort)
                        .build();

                JoinResponse response = stub.join(request);

                if (response.getAccepted()) {
                    System.out.println("Aileye katildi via " + HOST + ":" + port);

                    // Mevcut üyeleri ekle
                    for (String memberAddr : response.getMembersList()) {
                        String[] parts = memberAddr.split(":");
                        if (parts.length == 2) {
                            registry.addMember(parts[0], Integer.parseInt(parts[1]));
                        }
                    }
                }
            } catch (Exception e) {
                // Bu port'ta kimse yok, devam
            }
        }
    }

    private ManagedChannel getChannel(String host, int port) {
        String key = host + ":" + port;
        return channels.computeIfAbsent(key, k -> ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build());
    }

    private void startFamilyPrinter() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            registry.printFamily();
            System.out.println("Mesaj sayisi: " + diskStorage.getMessageCount());
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void startHealthChecker() {
        if (!registry.isLeader())
            return;

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            for (NodeRegistry.Member member : registry.getAliveMembers()) {
                try {
                    ManagedChannel channel = getChannel(member.host, member.port);
                    FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);

                    HealthResponse response = stub.health(HealthRequest.newBuilder().build());
                    if (!response.getAlive()) {
                        registry.markDead(member.host, member.port);
                    }
                } catch (Exception e) {
                    registry.markDead(member.host, member.port);
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    // ========== TCP Komut İşleyiciler ==========

    /**
     * SET komutu: mesajı kaydet ve tolerance kadar üyeye dağıt
     */
    public String handleSet(int id, String message) {
        // 1. Kendine kaydet
        boolean localSave = diskStorage.save(id, message);
        if (!localSave) {
            return "ERROR: Lokal kayit basarisiz";
        }

        // 2. Tolerance kadar üye seç
        List<NodeRegistry.Member> selected = registry.selectMembers(tolerance);
        List<NodeRegistry.Member> successful = new ArrayList<>();

        // 3. Seçilen üyelere gRPC ile gönder
        for (NodeRegistry.Member member : selected) {
            try {
                ManagedChannel channel = getChannel(member.host, member.port);
                StorageServiceGrpc.StorageServiceBlockingStub stub = StorageServiceGrpc.newBlockingStub(channel);

                StoredMessage msg = StoredMessage.newBuilder()
                        .setId(id)
                        .setText(message)
                        .build();

                StoreResult result = stub.store(msg);
                if (result.getSuccess()) {
                    successful.add(member);
                }
            } catch (Exception e) {
                System.err.println("Store hatasi " + member.getAddress() + ": " + e.getMessage());
                registry.markDead(member.host, member.port);
            }
        }

        // 4. Mesaj haritasını güncelle
        messageMap.put(id, successful);

        System.out.println("SET " + id + " -> " + (successful.size() + 1) + " node'a kaydedildi");
        return "OK";
    }

    /**
     * GET komutu: önce lokal, yoksa üyelerden oku
     */
    public String handleGet(int id) {
        // 1. Önce kendi diskinde ara
        String content = diskStorage.load(id);
        if (content != null) {
            return content;
        }

        // 2. Mesaj haritasına bak
        List<NodeRegistry.Member> members = messageMap.get(id);
        if (members == null || members.isEmpty()) {
            return "NOT_FOUND";
        }

        // 3. Üyelerden sırayla dene
        for (NodeRegistry.Member member : members) {
            if (!member.alive)
                continue;

            try {
                ManagedChannel channel = getChannel(member.host, member.port);
                StorageServiceGrpc.StorageServiceBlockingStub stub = StorageServiceGrpc.newBlockingStub(channel);

                MessageId request = MessageId.newBuilder().setId(id).build();
                StoredMessage response = stub.retrieve(request);

                if (!response.getText().isEmpty()) {
                    return response.getText();
                }
            } catch (Exception e) {
                System.err.println("Retrieve hatasi " + member.getAddress() + ": " + e.getMessage());
                registry.markDead(member.host, member.port);
            }
        }

        return "NOT_FOUND";
    }

    /**
     * STATS komutu: tüm üyelerin mesaj sayısını göster
     */
    public String handleStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CLUSTER STATS ===\n");
        sb.append("Tolerance: ").append(tolerance).append("\n");
        sb.append("Total members: ").append(registry.getMemberCount()).append("\n");
        sb.append("\n--- Node Message Counts ---\n");

        // Kendim (lider)
        sb.append("  ").append(registry.getSelf().getAddress())
                .append(" (leader): ").append(diskStorage.getMessageCount()).append(" messages\n");

        // Diğer üyeler
        for (NodeRegistry.Member member : registry.getAliveMembers()) {
            try {
                ManagedChannel channel = getChannel(member.host, member.port);
                FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);

                HealthResponse response = stub.health(HealthRequest.newBuilder().build());
                sb.append("  ").append(member.getAddress())
                        .append(": ").append(response.getMessageCount()).append(" messages\n");
            } catch (Exception e) {
                sb.append("  ").append(member.getAddress()).append(": UNREACHABLE\n");
                registry.markDead(member.host, member.port);
            }
        }

        // Dead üyeler
        for (NodeRegistry.Member member : registry.getAllMembers()) {
            if (!member.alive && !member.equals(registry.getSelf())) {
                sb.append("  ").append(member.getAddress()).append(": DEAD\n");
            }
        }

        sb.append("=====================");
        return sb.toString();
    }

    // ========== Port Bulma ==========

    private static int findAvailablePort() {
        for (int port = BASE_PORT; port <= MAX_PORT; port++) {
            try (ServerSocket ss = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                // Port kullanımda, sonrakine geç
            }
        }
        throw new RuntimeException("Kullanilabilir port bulunamadi");
    }

    // ========== Main ==========

    public static void main(String[] args) throws Exception {
        int port = findAvailablePort();
        NodeMain node = new NodeMain(port);
        node.start();
    }
}
