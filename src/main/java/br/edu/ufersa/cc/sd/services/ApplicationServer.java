package br.edu.ufersa.cc.sd.services;

import java.io.Serializable;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.utils.Constants;

public class ApplicationServer extends AbstractServer {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationServer.class.getSimpleName());

    private final OrderService orderService = new OrderService();

    public ApplicationServer() {
        super(LOG, Nature.APPLICATION, Constants.APPLICATION_PORT);
    }

    @Override
    protected Response<Serializable> handleMessage(final Request<? extends Serializable> request) {
        final var order = (Order) request.getItem();
        switch (request.getOperation()) {
            case LOCALIZE:
                return new Response<>(ResponseStatus.ERROR, "O servidor de Dados não faz Localização");

            case LIST:
                final var list = orderService.listAll();
                return new Response<>(new ArrayList<>(list));

            case CREATE:
                order.setCode(null);
                orderService.create(order);
                return new Response<>(ResponseStatus.OK);

            case FIND:
                try {
                    return new Response<>(orderService.findByCode(order.getCode()));
                } catch (final NotFoundException e) {
                    return new Response<>(ResponseStatus.ERROR, "Ordem não encontrada");
                }

            case UPDATE:
                orderService.update(order);
                return new Response<>(ResponseStatus.OK);

            case DELETE:
                orderService.delete(order);
                return new Response<>(ResponseStatus.OK);

            case COUNT:
                return new Response<>(orderService.countAll());

            default:
                return new Response<>(ResponseStatus.ERROR, "Operação não reconhecida");
        }
    }

}
