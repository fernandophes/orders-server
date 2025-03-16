package br.edu.ufersa.cc.sd.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum Nature {

    CLIENT("Cliente", 0),
    LOCALIZATION("Localização", 8400),
    PROXY("Proxy", 8420),
    APPLICATION("Aplicação", 8440);

    private final String name;
    private final Integer portRange;

}
