package com.example.family;

import io.grpc.stub.StreamObserver;

/**
 * StorageService gRPC implementasyonu.
 * Mesajları diske kaydeder/okur.
 */
public class StorageServiceImpl extends StorageServiceGrpc.StorageServiceImplBase {

    private final DiskStorage diskStorage;

    public StorageServiceImpl(DiskStorage diskStorage) {
        this.diskStorage = diskStorage;
    }

    @Override
    public void store(StoredMessage request, StreamObserver<StoreResult> responseObserver) {
        boolean success = diskStorage.save(request.getId(), request.getText());

        StoreResult result = StoreResult.newBuilder()
                .setSuccess(success)
                .setError(success ? "" : "Disk yazma hatası")
                .build();

        responseObserver.onNext(result);
        responseObserver.onCompleted();

        if (success) {
            System.out.println("Mesaj kaydedildi: id=" + request.getId());
        }
    }

    @Override
    public void retrieve(MessageId request, StreamObserver<StoredMessage> responseObserver) {
        String content = diskStorage.load(request.getId());

        StoredMessage msg;
        if (content != null) {
            msg = StoredMessage.newBuilder()
                    .setId(request.getId())
                    .setText(content)
                    .build();
        } else {
            msg = StoredMessage.newBuilder()
                    .setId(request.getId())
                    .setText("")
                    .build();
        }

        responseObserver.onNext(msg);
        responseObserver.onCompleted();
    }
}
