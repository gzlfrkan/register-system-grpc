package com.example.family;

import family.NodeInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NodeRegistry {

    private final Set<NodeInfo> dugumler = ConcurrentHashMap.newKeySet();

    public void add(NodeInfo dugum) {
        dugumler.add(dugum);
    }

    public void addAll(Collection<NodeInfo> digerler) {
        dugumler.addAll(digerler);
    }

    public List<NodeInfo> snapshot() {
        return List.copyOf(dugumler);
    }

    public void remove(NodeInfo dugum) {
        dugumler.remove(dugum);
    }

    public int boyut() {
        return dugumler.size();
    }

    public boolean icerir(NodeInfo dugum) {
        return dugumler.contains(dugum);
    }

    public void temizle() {
        dugumler.clear();
    }
}
