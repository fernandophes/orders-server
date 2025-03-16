package br.edu.ufersa.cc.sd.dto;

import java.io.Serializable;

import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Response<T extends Serializable> implements Serializable {

    private final ResponseStatus status;
    private final String message;
    private final T item;
    private final Class<T> type;

    public Response(ResponseStatus status) {
        this.status = status;
        this.message = null;
        this.item = null;
        this.type = null;
    }

    public Response(T item) {
        this(item, null);
    }

    public Response(T item, String message) {
        this(ResponseStatus.OK, message, item);
    }

    public Response(ResponseStatus status, String message) {
        this.status = status;
        this.message = message;
        this.item = null;
        this.type = null;
    }

    @SuppressWarnings("unchecked")
    public Response(ResponseStatus status, String message, T item) {
        this.status = status;
        this.message = message;
        this.item = item;
        this.type = (Class<T>) item.getClass();
    }

    public static <T extends Serializable> Response<T> ok() {
        return new Response<>(ResponseStatus.OK);
    }

    public static <T extends Serializable> Response<T> error() {
        return new Response<>(ResponseStatus.ERROR);
    }

}
