package com.example.family;

import java.io.*;
import java.nio.file.*;

/**
 * Disk üzerinde mesaj saklama sınıfı.
 * Her mesaj ayrı dosyada: messages/<id>.msg
 */
public class DiskStorage {

    private final Path messagesDir;
    private int messageCount = 0;

    public DiskStorage(String baseDir) {
        this.messagesDir = Paths.get(baseDir, "messages");
        try {
            Files.createDirectories(messagesDir);
            // Mevcut mesaj sayısını hesapla
            messageCount = (int) Files.list(messagesDir)
                    .filter(p -> p.toString().endsWith(".msg"))
                    .count();
        } catch (IOException e) {
            System.err.println("Mesaj dizini olusturulamadi: " + e.getMessage());
        }
    }

    /**
     * Mesajı diske kaydet (Buffered IO)
     */
    public boolean save(int id, String content) {
        Path file = messagesDir.resolve(id + ".msg");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write(content);
            if (!Files.exists(file)) {
                messageCount++;
            }
            return true;
        } catch (IOException e) {
            System.err.println("Mesaj kaydedilemedi [" + id + "]: " + e.getMessage());
            return false;
        }
    }

    /**
     * Mesajı diskten oku
     */
    public String load(int id) {
        Path file = messagesDir.resolve(id + ".msg");
        if (!Files.exists(file)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0)
                    sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            System.err.println("Mesaj okunamadi [" + id + "]: " + e.getMessage());
            return null;
        }
    }

    /**
     * Mesaj var mı?
     */
    public boolean exists(int id) {
        return Files.exists(messagesDir.resolve(id + ".msg"));
    }

    /**
     * Toplam mesaj sayısı
     */
    public int getMessageCount() {
        try {
            return (int) Files.list(messagesDir)
                    .filter(p -> p.toString().endsWith(".msg"))
                    .count();
        } catch (IOException e) {
            return messageCount;
        }
    }
}
