package com.example.auction.client;

import com.example.auction.common.net.Envelope;

public interface ClientEventListener {
    void onEvent(Envelope event);
}