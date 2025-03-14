package br.edu.ufersa.cc.sd.dto;

import java.io.Serializable;
import java.net.InetSocketAddress;

import br.edu.ufersa.cc.sd.enums.Nature;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class NotificationDto implements Serializable {

    private final Nature nature;
    private final InetSocketAddress oldAddress;
    private final InetSocketAddress newAddress;

}
