package br.edu.ufersa.cc.sd.services;

import java.io.Serializable;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.Notification;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.exceptions.ConnectionException;
import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.models.Order;

public class ProxyServer extends AbstractServer {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class.getSimpleName());

    private final CacheService cacheService = new CacheService();

    private final InetSocketAddress localizationAddress;
    private final InetSocketAddress applicationAddress;

    public ProxyServer(final InetSocketAddress localizationAddress, final InetSocketAddress applicationAddress) {
        super(LOG, Nature.PROXY);
        this.localizationAddress = localizationAddress;
        this.applicationAddress = applicationAddress;
    }

    @Override
    public void run() {
        super.run();

        if (!attachTo(localizationAddress)) {
            stop();
            throw new ConnectionException("Não foi possível se vincular ao servidor de localização");
        }
    }

    @Override
    public void stop() {
        if (!detachFrom(localizationAddress)) {
            LOG.warn("Não foi possível se desvincular do servidor de localização");
        }
        super.stop();
    }

    private Response<Order> getFromCache(final Request<Order> request) {
        final var result = cacheService.find(request.getItem().getCode(),
                () -> {
                    final Response<Order> resp = redirectRequestToServer(request);
                    if (resp.getStatus() == ResponseStatus.ERROR) {
                        throw new NotFoundException();
                    }
                    return resp.getItem();
                });

        if (result != null) {
            return new Response<>(result, "Ordem encontrada");
        } else {
            return new Response<>(ResponseStatus.ERROR, "A ordem não existe na base de dados");
        }
    }

    private Response<Order> updateIncludingCache(final Request<Order> request) {
        // Editar na base de dados
        final Response<Order> response = redirectRequestToServer(request);

        // Atualizar no cache
        cacheService.update(request.getItem());

        return response;
    }

    private Response<Order> deleteIncludingCache(final Request<Order> request) {
        // Remover na base de dados
        final Response<Order> response = redirectRequestToServer(request);

        // Remover no cache
        cacheService.delete(request.getItem());

        return response;
    }

    private <O extends Serializable> Response<O> redirectRequestToServer(final Request<Order> request) {
        return send(applicationAddress, request);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T extends Serializable> Response<T> handleMessage(Request<? extends Serializable> request) {
        final var orderRequest = (Request<Order>) request;
        switch (request.getOperation()) {
            case ATTACH:
                return attachProxy(request);

            case DETACH:
                return detachProxy(request);

            case LOCALIZE:
                return new Response<>(ResponseStatus.ERROR, "O servidor de Proxy não faz Localização");

            case FIND:
                return (Response<T>) getFromCache(orderRequest);

            case UPDATE:
                return (Response<T>) updateIncludingCache(orderRequest);

            case DELETE:
                return (Response<T>) deleteIncludingCache(orderRequest);

            default:
                return redirectRequestToServer(orderRequest);
        }
    }

    private <T extends Serializable> Response<T> attachProxy(final Request<? extends Serializable> request) {
        final var item = request.getItem();
        if (item instanceof Notification && ((Notification) item).getNature().equals(Nature.PROXY)) {
            final var notification = (Notification) item;
            final var newProxyAddress = notification.getAddress();

            // Adicionar ao set
            replicasAddresses.add(newProxyAddress);
            LOG.info("Recebido novo endereço de Proxy: {}", newProxyAddress);

            return Response.ok();
        } else {
            return Response.error();
        }
    }

    private <T extends Serializable> Response<T> detachProxy(final Request<? extends Serializable> request) {
        final var item = request.getItem();
        if (item instanceof Notification && ((Notification) item).getNature().equals(Nature.PROXY)) {
            final var notification = (Notification) item;
            final var oldProxyAddress = notification.getAddress();

            // Adicionar ao set
            replicasAddresses.remove(oldProxyAddress);
            LOG.info("Excluído endereço de Proxy: {}", oldProxyAddress);

            return Response.ok();
        } else {
            return Response.error();
        }
    }

}
