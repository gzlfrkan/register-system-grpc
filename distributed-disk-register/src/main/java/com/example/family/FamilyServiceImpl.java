package com.example.family;

import io.grpc.stub.StreamObserver;

/**
 * FamilyService gRPC implementasyonu.
 * Üye yönetimi: Join, Health, NotifyNewMember
 */
public class FamilyServiceImpl extends FamilyServiceGrpc.FamilyServiceImplBase {

    private final NodeRegistry registry;
    private final DiskStorage diskStorage;

    public FamilyServiceImpl(NodeRegistry registry, DiskStorage diskStorage) {
        this.registry = registry;
        this.diskStorage = diskStorage;
    }

    @Override
    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        String host = request.getHost();
        int port = request.getPort();

        // Yeni üyeyi ekle
        registry.addMember(host, port);

        // Mevcut üye listesini döndür
        JoinResponse response = JoinResponse.newBuilder()
                .setAccepted(true)
                .addAllMembers(registry.getMemberAddresses())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        System.out.println("Join kabul edildi: " + host + ":" + port);
    }

    @Override
    public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
        HealthResponse response = HealthResponse.newBuilder()
                .setAlive(true)
                .setMessageCount(diskStorage.getMessageCount())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void notifyNewMember(NewMemberNotification request, StreamObserver<Empty> responseObserver) {
        registry.addMember(request.getHost(), request.getPort());

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
