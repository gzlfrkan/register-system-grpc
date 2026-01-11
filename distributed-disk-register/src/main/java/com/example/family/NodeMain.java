package com.example.family;

import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import family.ChatMessage;
import family.KeyRequest;
import family.ValueResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.File;
import java.net.Socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class NodeMain {

    private static final int BASLANGIC_PORT = 5555;
    private static final int YAZDIR_ARALIK_SANIYE = 10;
    private static final int TCP_DINLEME_PORT = 6666;
    private static final int UDP_KESIF_PORT = 5554;
    private static String YEREL_ADRES = "127.0.0.1"; // Dinamik olarak belirlenecek
    private static String LIDER_ADRES = null; // Keşfedilecek
    private static final DateTimeFormatter ZAMAN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Performans istatistikleri
    private static final AtomicLong toplamSetSayisi = new AtomicLong(0);
    private static final AtomicLong toplamGetSayisi = new AtomicLong(0);
    private static final AtomicLong toplamYazmaSuresi = new AtomicLong(0);
    private static final AtomicLong toplamOkumaSuresi = new AtomicLong(0);
    private static final AtomicLong basariliReplikasyon = new AtomicLong(0);

    // Paylaşılan bellek ve disk I/O
    private static ConcurrentHashMap<Integer, String> bellek = new ConcurrentHashMap<>();
    private static DiskIO diskIO;
    private static int tolerance = 1; // Kaç düğüme replike edilecek
    private static volatile boolean liderMiyim = false;

    public static void main(String[] args) throws Exception {
        // Gerçek ağ IP adresini bul
        YEREL_ADRES = yerelIPBul();

        // Komut satırı argümanlarını işle
        DiskIO.Mode ioMode = DiskIO.Mode.CLASSIC;
        for (String arg : args) {
            if (arg.startsWith("--mode=")) {
                String modStr = arg.substring("--mode=".length()).toUpperCase();
                try {
                    ioMode = DiskIO.Mode.valueOf(modStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("Gecersiz mod: " + modStr);
                    System.err.println("Gecerli modlar: CLASSIC, UNBUFFERED, MEMORY_MAPPED");
                }
            } else if (arg.startsWith("--tolerance=")) {
                tolerance = Integer.parseInt(arg.substring("--tolerance=".length()));
            } else if (arg.startsWith("--leader=")) {
                LIDER_ADRES = arg.substring("--leader=".length());
            }
        }

        System.out.println("==========================================");
        System.out.println("  DISTRIBUTED SYSTEM - COOL VERSION v3.0");
        System.out.println("  Network Auto-Discovery + Multi-Computer");
        System.out.println("==========================================");
        System.out.printf("Local IP: %s%n", YEREL_ADRES);

        // Eğer lider adresi verilmemişse, ağda lider ara
        if (LIDER_ADRES == null) {
            System.out.println("Agda lider araniyor...");
            LIDER_ADRES = liderKesfi();
        }

        int port;
        if (LIDER_ADRES == null) {
            // Lider bulunamadı - ben lider olacağım
            port = BASLANGIC_PORT;
            liderMiyim = true;
            System.out.println("Lider bulunamadi - BEN LIDER OLUYORUM");
        } else {
            // Lider bulundu - ona bağlan
            port = bosPortBul(BASLANGIC_PORT);
            liderMiyim = false;
            System.out.printf("Lider bulundu: %s%n", LIDER_ADRES);
        }

        // Her düğüm kendi veri dizinine yazar
        String veriDizini = "data_" + YEREL_ADRES.replace(".", "_") + "_" + port;
        diskIO = new DiskIO(ioMode, veriDizini);

        NodeInfo kendim = NodeInfo.newBuilder()
                .setHost(YEREL_ADRES)
                .setPort(port)
                .build();

        NodeRegistry kayitci = new NodeRegistry();
        FamilyServiceImpl servis = new FamilyServiceImpl(kayitci, kendim, diskIO, bellek);

        Server sunucu = ServerBuilder
                .forPort(port)
                .addService(servis)
                .build()
                .start();

        System.out.printf("Node: %s:%d%n", YEREL_ADRES, port);
        System.out.printf("I/O Mode: %s%n", diskIO.getModeName());
        System.out.printf("Tolerance: %d (data copied to %d nodes)%n", tolerance, tolerance + 1);
        System.out.printf("Data Directory: %s%n", veriDizini);
        System.out.println("Started: " + LocalDateTime.now().format(ZAMAN_FORMAT));

        if (liderMiyim) {
            System.out.println("Role: LEADER");
            liderDinleyicisiniBaslat(kayitci, kendim);
            liderDuyurusunuBaslat(); // UDP broadcast başlat
        } else {
            System.out.println("Role: FOLLOWER");
            // Lidere bağlan
            lidereBaglan(LIDER_ADRES, kayitci, kendim);
        }

        System.out.println("------------------------------------------");

        if (liderMiyim) {
            istatistikYazicisiniBaslat(kayitci, kendim);
        }
        saglikKontrolunuBaslat(kayitci, kendim);

        sunucu.awaitTermination();
    }

    /**
     * Gerçek ağ IP adresini bulur (127.0.0.1 değil)
     */
    private static String yerelIPBul() {
        try {
            // Önce aktif ağ bağlantısını bulmaya çalış
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 80);
                String ip = socket.getLocalAddress().getHostAddress();
                if (!ip.equals("0.0.0.0") && !ip.startsWith("127.")) {
                    return ip;
                }
            } catch (Exception e) {
                // Devam et
            }

            // Alternatif yöntem: ağ arayüzlerini tara
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp())
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (!ip.startsWith("127.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("IP bulunamadi: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    /**
     * UDP broadcast ile ağda lider arar
     */
    private static String liderKesfi() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(3000); // 3 saniye bekle

            // Broadcast mesajı gönder
            String mesaj = "LEADER_SEARCH";
            byte[] sendData = mesaj.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length,
                    InetAddress.getByName("255.255.255.255"), UDP_KESIF_PORT);
            socket.send(sendPacket);

            // Yanıt bekle
            byte[] receiveData = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            try {
                socket.receive(receivePacket);
                String yanit = new String(receivePacket.getData(), 0, receivePacket.getLength());
                if (yanit.startsWith("LEADER_HERE:")) {
                    return yanit.substring("LEADER_HERE:".length());
                }
            } catch (SocketTimeoutException e) {
                // Zaman aşımı - lider yok
            }

        } catch (Exception e) {
            System.err.println("Lider arama hatasi: " + e.getMessage());
        }
        return null;
    }

    /**
     * Leader olarak UDP broadcast dinle ve kendini duyur
     */
    private static void liderDuyurusunuBaslat() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(UDP_KESIF_PORT)) {
                socket.setBroadcast(true);
                byte[] receiveData = new byte[256];

                System.out.printf("UDP Discovery: Port %d dinleniyor%n", UDP_KESIF_PORT);

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    String mesaj = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    if (mesaj.equals("LEADER_SEARCH")) {
                        // Liderin burada olduğunu bildir
                        String yanit = "LEADER_HERE:" + YEREL_ADRES;
                        byte[] sendData = yanit.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(
                                sendData, sendData.length,
                                receivePacket.getAddress(), receivePacket.getPort());
                        socket.send(sendPacket);

                        System.out.printf("[UDP] Lider arama yanıtlandı: %s%n",
                                receivePacket.getAddress().getHostAddress());
                    }
                }
            } catch (Exception e) {
                System.err.println("UDP duyuru hatasi: " + e.getMessage());
            }
        }, "LiderDuyurusu").start();
    }

    /**
     * Follower olarak lidere bağlan
     */
    private static void lidereBaglan(String liderIP, NodeRegistry kayitci, NodeInfo kendim) {
        ManagedChannel kanal = null;
        try {
            kanal = ManagedChannelBuilder
                    .forAddress(liderIP, BASLANGIC_PORT)
                    .usePlaintext()
                    .build();

            FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(kanal);

            FamilyView gorunum = stub.join(kendim);
            kayitci.addAll(gorunum.getMembersList());

            System.out.printf("Aileye katilindi: %d uye%n", kayitci.snapshot().size());

        } catch (Exception e) {
            System.err.println("Lidere baglanti hatasi: " + e.getMessage());
        } finally {
            if (kanal != null)
                kanal.shutdownNow();
        }
    }

    private static void liderDinleyicisiniBaslat(NodeRegistry kayitci, NodeInfo kendim) {
        new Thread(() -> {
            try (ServerSocket dinleyici = new ServerSocket(TCP_DINLEME_PORT)) {
                System.out.printf("TCP Listening: %s:%d%n", kendim.getHost(), TCP_DINLEME_PORT);

                while (true) {
                    Socket istemci = dinleyici.accept();
                    new Thread(() -> istemciBaglantisiniIsle(istemci, kayitci, kendim)).start();
                }

            } catch (IOException e) {
                System.err.println("TCP dinleyici hatasi: " + e.getMessage());
            }
        }, "LiderTcpDinleyici").start();
    }

    private static void istemciBaglantisiniIsle(Socket istemci, NodeRegistry kayitci, NodeInfo kendim) {
        String istemciAdresi = istemci.getRemoteSocketAddress().toString();
        System.out.println("[BAGLANTI] " + istemciAdresi);

        try (BufferedReader okuyucu = new BufferedReader(new InputStreamReader(istemci.getInputStream()));
                PrintWriter yazici = new PrintWriter(istemci.getOutputStream(), true)) {

            String satir;
            while ((satir = okuyucu.readLine()) != null) {
                String komut = satir.trim();
                if (komut.isEmpty())
                    continue;

                String yanit = komutuIsle(komut, kayitci, kendim);
                yazici.println(yanit);
            }

        } catch (IOException e) {
            // Bağlantı koptu - sessizce devam
        } finally {
            try {
                istemci.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String komutuIsle(String komut, NodeRegistry kayitci, NodeInfo kendim) {
        String[] parcalar = komut.split(" ", 3);

        if (parcalar.length < 2) {
            return "ERROR Invalid command format";
        }

        String islem = parcalar[0].toUpperCase();

        try {
            int anahtar = Integer.parseInt(parcalar[1]);

            if (islem.equals("SET")) {
                if (parcalar.length < 3) {
                    return "ERROR SET requires key and value";
                }
                String deger = parcalar[2];

                // Yerel belleğe kaydet
                bellek.put(anahtar, deger);

                // Yerel diske kaydet ve süreyi ölç
                long yazmaSuresi = diskIO.write(anahtar, deger);
                toplamYazmaSuresi.addAndGet(yazmaSuresi);
                toplamSetSayisi.incrementAndGet();

                // Konsola yaz (kısa)
                System.out.printf("[SET] %d (%d B) disk: %d us%n",
                        anahtar, deger.length(), yazmaSuresi);

                // Diğer düğümlere replike et
                int replikeSayisi = replikasyonYap(kayitci, kendim, anahtar, deger);
                basariliReplikasyon.addAndGet(replikeSayisi);

                return "OK";

            } else if (islem.equals("GET")) {
                toplamGetSayisi.incrementAndGet();
                long baslangic = System.nanoTime();

                // Önce yerel bellekte ara
                String deger = bellek.get(anahtar);

                // Yerel bellekte yoksa yerel diskten oku
                if (deger == null) {
                    deger = diskIO.read(anahtar);
                    if (deger != null) {
                        bellek.put(anahtar, deger);
                    }
                }

                // Yerel bulunamadıysa diğer düğümlerden sor
                if (deger == null) {
                    deger = digerDugumlerdenAl(kayitci, kendim, anahtar);
                    if (deger != null) {
                        // Yerel cache'e kaydet
                        bellek.put(anahtar, deger);
                    }
                }

                long okumaSuresi = (System.nanoTime() - baslangic) / 1000;
                toplamOkumaSuresi.addAndGet(okumaSuresi);

                if (deger != null) {
                    System.out.printf("[GET] %d -> found (%d B) %d us%n",
                            anahtar, deger.length(), okumaSuresi);
                    return "OK " + deger;
                } else {
                    System.out.printf("[GET] %d -> not found %d us%n", anahtar, okumaSuresi);
                    return "OK";
                }

            } else {
                return "ERROR Unknown command: " + islem;
            }

        } catch (NumberFormatException e) {
            return "ERROR Invalid key format";
        } catch (Exception e) {
            return "ERROR " + e.getMessage();
        }
    }

    /**
     * Veriyi diğer düğümlere replike eder (tolerance kadar)
     */
    private static int replikasyonYap(NodeRegistry kayitci, NodeInfo kendim, int anahtar, String deger) {
        List<NodeInfo> uyeler = kayitci.snapshot();
        int replikeSayisi = 0;

        for (NodeInfo uye : uyeler) {
            if (uye.getHost().equals(kendim.getHost()) && uye.getPort() == kendim.getPort()) {
                continue;
            }

            if (replikeSayisi >= tolerance) {
                break; // Yeterli replika yapıldı
            }

            ManagedChannel kanal = null;
            try {
                kanal = ManagedChannelBuilder
                        .forAddress(uye.getHost(), uye.getPort())
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(kanal);

                ChatMessage mesaj = ChatMessage.newBuilder()
                        .setText("SET:" + anahtar + ":" + deger)
                        .setFromHost(kendim.getHost())
                        .setFromPort(kendim.getPort())
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                stub.receiveChat(mesaj);
                replikeSayisi++;

            } catch (Exception e) {
                // Düğüm erişilemez - devam et
            } finally {
                if (kanal != null)
                    kanal.shutdownNow();
            }
        }

        return replikeSayisi;
    }

    /**
     * Yerel bulunamayan veriyi diğer düğümlerden alır
     */
    private static String digerDugumlerdenAl(NodeRegistry kayitci, NodeInfo kendim, int anahtar) {
        List<NodeInfo> uyeler = kayitci.snapshot();

        for (NodeInfo uye : uyeler) {
            if (uye.getHost().equals(kendim.getHost()) && uye.getPort() == kendim.getPort()) {
                continue;
            }

            ManagedChannel kanal = null;
            try {
                kanal = ManagedChannelBuilder
                        .forAddress(uye.getHost(), uye.getPort())
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(kanal);

                KeyRequest istek = KeyRequest.newBuilder()
                        .setKey(anahtar)
                        .build();

                ValueResponse yanit = stub.getValue(istek);

                if (yanit.getFound()) {
                    System.out.printf("[GET] %d -> %s:%d uzerinden bulundu%n",
                            anahtar, uye.getHost(), uye.getPort());
                    return yanit.getValue();
                }

            } catch (Exception e) {
                // Düğüm erişilemez - devam et
            } finally {
                if (kanal != null)
                    kanal.shutdownNow();
            }
        }

        return null;
    }

    private static int bosPortBul(int baslangicPort) {
        int port = baslangicPort;
        while (true) {
            try (ServerSocket test = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private static void mevcutDugumleriKesifEt(String adres, int benimPort, NodeRegistry kayitci, NodeInfo kendim) {
        for (int port = BASLANGIC_PORT; port < benimPort; port++) {
            ManagedChannel kanal = null;
            try {
                kanal = ManagedChannelBuilder
                        .forAddress(adres, port)
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(kanal);

                FamilyView gorunum = stub.join(kendim);
                kayitci.addAll(gorunum.getMembersList());

                System.out.printf("Joined family: %d members%n", kayitci.snapshot().size());

            } catch (Exception ignored) {
            } finally {
                if (kanal != null)
                    kanal.shutdownNow();
            }
        }
    }

    private static void istatistikYazicisiniBaslat(NodeRegistry kayitci, NodeInfo kendim) {
        ScheduledExecutorService zamanlayici = Executors.newSingleThreadScheduledExecutor();

        zamanlayici.scheduleAtFixedRate(() -> {
            List<NodeInfo> uyeler = kayitci.snapshot();
            long setSayisi = toplamSetSayisi.get();
            long getSayisi = toplamGetSayisi.get();
            long yazmaSuresi = toplamYazmaSuresi.get();
            long okumaSuresi = toplamOkumaSuresi.get();
            long replike = basariliReplikasyon.get();

            System.out.println();
            System.out.println("+------------------------------------------+");
            System.out.println("|         PERFORMANCE STATISTICS           |");
            System.out.println("+------------------------------------------+");
            System.out.printf("| Node: %s:%-26d|%n", kendim.getHost(), kendim.getPort());
            System.out.printf("| I/O Mode: %-31s|%n", diskIO.getModeName());
            System.out.printf("| Time: %-34s|%n", LocalDateTime.now().format(ZAMAN_FORMAT));
            System.out.println("+------------------------------------------+");
            System.out.printf("| Total SET: %-30d|%n", setSayisi);
            System.out.printf("| Total GET: %-30d|%n", getSayisi);
            System.out.printf("| Records in Memory: %-22d|%n", bellek.size());
            System.out.printf("| Successful Replications: %-16d|%n", replike);
            System.out.println("+------------------------------------------+");
            if (setSayisi > 0) {
                System.out.printf("| Avg Write Time: %-22d us |%n", yazmaSuresi / setSayisi);
            }
            if (getSayisi > 0) {
                System.out.printf("| Avg Read Time: %-23d us |%n", okumaSuresi / getSayisi);
            }
            System.out.println("+------------------------------------------+");
            System.out.printf("| Active Nodes: %-27d|%n", uyeler.size());
            for (NodeInfo uye : uyeler) {
                boolean benMiyim = uye.getHost().equals(kendim.getHost()) && uye.getPort() == kendim.getPort();
                System.out.printf("|   - %s:%-5d %-23s|%n",
                        uye.getHost(), uye.getPort(), benMiyim ? "(ME)" : "");
            }
            System.out.println("+------------------------------------------+");

        }, 3, YAZDIR_ARALIK_SANIYE, TimeUnit.SECONDS);
    }

    private static void saglikKontrolunuBaslat(NodeRegistry kayitci, NodeInfo kendim) {
        ScheduledExecutorService zamanlayici = Executors.newSingleThreadScheduledExecutor();

        zamanlayici.scheduleAtFixedRate(() -> {
            List<NodeInfo> uyeler = kayitci.snapshot();

            for (NodeInfo uye : uyeler) {
                if (uye.getHost().equals(kendim.getHost()) && uye.getPort() == kendim.getPort()) {
                    continue;
                }

                ManagedChannel kanal = null;
                try {
                    kanal = ManagedChannelBuilder
                            .forAddress(uye.getHost(), uye.getPort())
                            .usePlaintext()
                            .build();

                    FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(kanal);

                    stub.getFamily(Empty.newBuilder().build());

                } catch (Exception e) {
                    System.out.printf("! Dugum erislemez: %s:%d%n", uye.getHost(), uye.getPort());
                    kayitci.remove(uye);
                } finally {
                    if (kanal != null) {
                        kanal.shutdownNow();
                    }
                }
            }

        }, 5, 10, TimeUnit.SECONDS);
    }
}
