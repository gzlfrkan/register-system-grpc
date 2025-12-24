package com.example.family;

import java.util.*;
import java.util.concurrent.*;

/**
 * Aile üyelerini yöneten registry.
 */
public class NodeRegistry {

    // Üye bilgisi
    public static class Member {
        public final String host;
        public final int port;
        public volatile boolean alive;

        public Member(String host, int port) {
            this.host = host;
            this.port = port;
            this.alive = true;
        }

        public String getAddress() {
            return host + ":" + port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Member member = (Member) o;
            return port == member.port && host.equals(member.host);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, port);
        }

        @Override
        public String toString() {
            return getAddress() + (alive ? "" : " [DEAD]");
        }
    }

    private final List<Member> members = new CopyOnWriteArrayList<>();
    private final Member self;
    private int roundRobinIndex = 0;

    public NodeRegistry(String host, int port) {
        this.self = new Member(host, port);
        members.add(self);
    }

    public Member getSelf() {
        return self;
    }

    public boolean isLeader() {
        return self.port == 5555;
    }

    /**
     * Yeni üye ekle
     */
    public void addMember(String host, int port) {
        Member m = new Member(host, port);
        if (!members.contains(m)) {
            members.add(m);
            System.out.println("Yeni uye eklendi: " + m.getAddress());
        }
    }

    /**
     * Üyeyi ölü işaretle
     */
    public void markDead(String host, int port) {
        for (Member m : members) {
            if (m.host.equals(host) && m.port == port) {
                m.alive = false;
                System.out.println("Uye olu isaretlendi: " + m.getAddress());
                break;
            }
        }
    }

    /**
     * Üyeyi çıkar
     */
    public void removeMember(String host, int port) {
        members.removeIf(m -> m.host.equals(host) && m.port == port);
    }

    /**
     * Tüm üyeleri getir
     */
    public List<Member> getAllMembers() {
        return new ArrayList<>(members);
    }

    /**
     * Hayatta olan üyeleri getir (kendisi hariç)
     */
    public List<Member> getAliveMembers() {
        List<Member> alive = new ArrayList<>();
        for (Member m : members) {
            if (m.alive && !m.equals(self)) {
                alive.add(m);
            }
        }
        return alive;
    }

    /**
     * Round-robin ile n adet üye seç
     */
    public List<Member> selectMembers(int count) {
        List<Member> alive = getAliveMembers();
        if (alive.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        List<Member> selected = new ArrayList<>();
        int selectCount = Math.min(count, alive.size());

        for (int i = 0; i < selectCount; i++) {
            int idx = (roundRobinIndex + i) % alive.size();
            selected.add(alive.get(idx));
        }

        roundRobinIndex = (roundRobinIndex + selectCount) % alive.size();
        return selected;
    }

    /**
     * Aile listesini ekrana bas
     */
    public void printFamily() {
        System.out.println("======================================");
        System.out.println("Family at " + self.getAddress() + " (me)");
        System.out.println("Time: " + java.time.LocalDateTime.now());
        System.out.println("Members:");
        for (Member m : members) {
            String marker = m.equals(self) ? " (me)" : "";
            String status = m.alive ? "" : " [DEAD]";
            System.out.println("  - " + m.getAddress() + marker + status);
        }
        System.out.println("======================================");
    }

    /**
     * Üye sayısı
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Üye adreslerini string listesi olarak döndür
     */
    public List<String> getMemberAddresses() {
        List<String> addresses = new ArrayList<>();
        for (Member m : members) {
            addresses.add(m.getAddress());
        }
        return addresses;
    }
}
