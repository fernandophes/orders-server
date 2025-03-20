package br.edu.ufersa.cc.sd.dto;

import java.io.Serializable;
import java.net.InetSocketAddress;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class Combo implements Serializable {

    private final InetSocketAddress serverSocketAddress;
    private final InetSocketAddress remoteAddress;

}
