package br.edu.ufersa.cc.sd.dto;

import java.io.Serializable;

import br.edu.ufersa.cc.sd.enums.Operation;
import lombok.Data;

@Data
public class Request<I extends Serializable> implements Serializable {

    private final Operation operation;
    private final I item;
    private final Class<I> type;

    @SuppressWarnings("unchecked")
    public Request(final Operation operation, final I item) {
        this.operation = operation;
        this.item = item;
        this.type = (Class<I>) item.getClass();
    }

    public Request(final Operation operation, final Class<I> type) {
        this.operation = operation;
        this.type = type;
        this.item = null;
    }

}
