package com.example.family;

import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import family.ChatMessage;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

public class FamilyServiceImpl extends FamilyServiceGrpc.FamilyServiceImplBase {

    private final NodeRegistry kayitci;
    private final NodeInfo kendim;
    private final DiskIO diskIO;
    private final ConcurrentHashMap<Integer, String> bellek;
    private static final DateTimeFormatter ZAMAN_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public FamilyServiceImpl(NodeRegistry kayitci, NodeInfo kendim, DiskIO diskIO,
            ConcurrentHashMap<Integer, String> bellek) {
        this.kayitci = kayitci;
        this.kendim = kendim;
        this.diskIO = diskIO;
        this.bellek = bellek;
        this.kayitci.add(kendim);
    }

    @Override
    public void join(NodeInfo istek, StreamObserver<FamilyView> cevapGozlemci) {
        kayitci.add(istek);

        System.out.printf("+ New member joined: %s:%d%n", istek.getHost(), istek.getPort());

        FamilyView gorunum = FamilyView.newBuilder()
                .addAllMembers(kayitci.snapshot())
                .build();

        cevapGozlemci.onNext(gorunum);
        cevapGozlemci.onCompleted();
    }

    @Override
    public void getFamily(Empty istek, StreamObserver<FamilyView> cevapGozlemci) {
        FamilyView gorunum = FamilyView.newBuilder()
                .addAllMembers(kayitci.snapshot())
                .build();

        cevapGozlemci.onNext(gorunum);
        cevapGozlemci.onCompleted();
    }

    @Override
    public void receiveChat(ChatMessage istek, StreamObserver<Empty> cevapGozlemci) {
        String metin = istek.getText();

        // SET komutlarını işle ve yerel diske kaydet (REPLIKASYON)
        if (metin.startsWith("SET:")) {
            try {
                String[] parcalar = metin.split(":", 3);
                if (parcalar.length >= 3) {
                    int anahtar = Integer.parseInt(parcalar[1]);
                    String deger = parcalar[2];

                    // Belleğe kaydet
                    bellek.put(anahtar, deger);

                    // Diske kaydet
                    long writeTimeUs = diskIO.write(anahtar, deger);

                    System.out.printf("[REPLICATION] SET %d (%d bytes) -> disk write: %d us%n",
                            anahtar, deger.length(), writeTimeUs);
                }
            } catch (Exception e) {
                System.err.println("Replication error: " + e.getMessage());
            }
        } else {
            // Normal mesaj gösterimi
            LocalDateTime zamanLocal = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(istek.getTimestamp()),
                    ZoneId.systemDefault());

            System.out.println();
            System.out.println("############################################");
            System.out.println("#           GELEN MESAJ                    #");
            System.out.println("############################################");
            System.out.println("  Gonderen: " + istek.getFromHost() + ":" + istek.getFromPort());
            System.out.println("  Icerik  : " + metin);
            System.out.println("  Zaman   : " + zamanLocal.format(ZAMAN_FORMAT));
            System.out.println("############################################");
            System.out.println();
        }

        cevapGozlemci.onNext(Empty.newBuilder().build());
        cevapGozlemci.onCompleted();
    }

    /**
     * Diğer düğümden GET isteği - yerel bellekten veya diskten veri döner
     */
    @Override
    public void getValue(family.KeyRequest istek, StreamObserver<family.ValueResponse> cevapGozlemci) {
        int anahtar = istek.getKey();
        String deger = bellek.get(anahtar);

        if (deger == null) {
            try {
                deger = diskIO.read(anahtar);
                if (deger != null) {
                    bellek.put(anahtar, deger);
                }
            } catch (Exception e) {
                System.err.println("Disk okuma hatasi: " + e.getMessage());
            }
        }

        family.ValueResponse yanit = family.ValueResponse.newBuilder()
                .setKey(anahtar)
                .setValue(deger != null ? deger : "")
                .setFound(deger != null)
                .build();

        cevapGozlemci.onNext(yanit);
        cevapGozlemci.onCompleted();
    }
}
