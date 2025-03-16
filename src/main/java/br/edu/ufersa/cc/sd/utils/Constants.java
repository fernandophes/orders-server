package br.edu.ufersa.cc.sd.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class Constants {

    public static final Integer RANGE_SIZE = 20;

    public static final String getDefaultHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (final UnknownHostException e) {
            return "localhost";
        }
    }

}
