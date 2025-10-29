package com.microservices.autolock;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Face Watch Service...");
        FaceWatchService service = new FaceWatchService();
        service.start();
    }
}
