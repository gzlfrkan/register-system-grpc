package com.example.family;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;

/**
 * Farklı disk I/O stratejilerini uygulayan sınıf.
 * - CLASSIC: BufferedWriter (varsayılan, hızlı ama buffer'lı)
 * - UNBUFFERED: FileOutputStream (doğrudan yazma, her byte anında diske)
 * - MEMORY_MAPPED: MappedByteBuffer (zero-copy, en hızlı büyük veriler için)
 */
public class DiskIO {

    public enum Mode {
        CLASSIC, // BufferedWriter - varsayılan Java I/O
        UNBUFFERED, // FileOutputStream - direct write
        MEMORY_MAPPED // MappedByteBuffer - zero-copy
    }

    private final Mode mode;
    private final String veriDizini;

    public DiskIO(Mode mode, String veriDizini) {
        this.mode = mode;
        this.veriDizini = veriDizini;

        File klasor = new File(veriDizini);
        if (!klasor.exists()) {
            klasor.mkdirs();
        }
    }

    public String getModeName() {
        return mode.name();
    }

    /**
     * Veriyi diske yazar - seçili moda göre farklı strateji kullanır
     */
    public long write(int anahtar, String deger) throws IOException {
        File dosya = new File(veriDizini, anahtar + ".msg");
        byte[] veri = deger.getBytes("UTF-8");

        long baslangic = System.nanoTime();

        switch (mode) {
            case CLASSIC:
                writeClassic(dosya, veri);
                break;
            case UNBUFFERED:
                writeUnbuffered(dosya, veri);
                break;
            case MEMORY_MAPPED:
                writeMemoryMapped(dosya, veri);
                break;
        }

        long bitis = System.nanoTime();
        return (bitis - baslangic) / 1000; // microseconds
    }

    /**
     * CLASSIC: BufferedWriter kullanarak yazma
     * - Veriyi önce buffer'a yazar, dolu olunca diske flush eder
     * - Küçük yazmalarda verimli, büyük batch işlemlerde iyi
     */
    private void writeClassic(File dosya, byte[] veri) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dosya))) {
            bos.write(veri);
            bos.flush();
        }
    }

    /**
     * UNBUFFERED: Doğrudan FileOutputStream ile yazma
     * - Her byte anında diske yazılır
     * - Veri güvenliği yüksek ama daha yavaş
     * - sync() ile diske zorla yazma
     */
    private void writeUnbuffered(File dosya, byte[] veri) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dosya)) {
            fos.write(veri);
            fos.getFD().sync(); // Veriyi anında diske yaz
        }
    }

    /**
     * MEMORY_MAPPED: Zero-copy yazma
     * - Dosyayı doğrudan belleğe map'ler
     * - Kernel buffer'ı atlar, en hızlı yöntem
     * - Büyük dosyalar için ideal
     */
    private void writeMemoryMapped(File dosya, byte[] veri) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(dosya, "rw");
                FileChannel kanal = raf.getChannel()) {

            MappedByteBuffer buffer = kanal.map(
                    FileChannel.MapMode.READ_WRITE,
                    0,
                    veri.length);
            buffer.put(veri);
            buffer.force(); // Değişiklikleri diske yaz
        }
    }

    /**
     * Diskten veri okur
     */
    public String read(int anahtar) throws IOException {
        File dosya = new File(veriDizini, anahtar + ".msg");
        if (!dosya.exists()) {
            return null;
        }

        switch (mode) {
            case CLASSIC:
                return readClassic(dosya);
            case UNBUFFERED:
                return readUnbuffered(dosya);
            case MEMORY_MAPPED:
                return readMemoryMapped(dosya);
            default:
                return readClassic(dosya);
        }
    }

    private String readClassic(File dosya) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(dosya))) {
            return new String(bis.readAllBytes(), "UTF-8");
        }
    }

    private String readUnbuffered(File dosya) throws IOException {
        try (FileInputStream fis = new FileInputStream(dosya)) {
            return new String(fis.readAllBytes(), "UTF-8");
        }
    }

    private String readMemoryMapped(File dosya) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(dosya, "r");
                FileChannel kanal = raf.getChannel()) {

            MappedByteBuffer buffer = kanal.map(
                    FileChannel.MapMode.READ_ONLY,
                    0,
                    kanal.size());
            byte[] veri = new byte[(int) kanal.size()];
            buffer.get(veri);
            return new String(veri, "UTF-8");
        }
    }

    /**
     * Dosya var mı kontrol et
     */
    public boolean exists(int anahtar) {
        return new File(veriDizini, anahtar + ".msg").exists();
    }
}
