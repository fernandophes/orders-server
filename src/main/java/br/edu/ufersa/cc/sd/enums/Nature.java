package br.edu.ufersa.cc.sd.enums;

import java.rmi.Remote;

import br.edu.ufersa.cc.sd.contracts.RemoteApplication;
import br.edu.ufersa.cc.sd.contracts.RemoteProxy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum Nature {

    CLIENT("Cliente", 0, 0, null),
    LOCALIZATION("Localização", 8400, 8500, null),
    PROXY("Proxy", 8420, 8520, RemoteProxy.class),
    APPLICATION("Aplicação", 8440, 8540, RemoteApplication.class);

    private final String name;
    private final Integer socketPortRange;
    private final Integer remotePortRange;
    private final Class<? extends Remote> remoteSkeleton;

}
