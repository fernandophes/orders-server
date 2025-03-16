package br.edu.ufersa.cc.sd.services;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.Notification;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.Operation;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;

public class LocalizationServer extends AbstractServer {

    private static final Logger LOG = LoggerFactory.getLogger(LocalizationServer.class.getSimpleName());

    private static Set<InetSocketAddress> proxiesAddresses = new HashSet<>();

    public LocalizationServer() {
        super(LOG, Nature.LOCALIZATION);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T extends Serializable> Response<T> handleMessage(Request<? extends Serializable> request) {
        switch (request.getOperation()) {
            case LOCALIZE:
                final var chosenProxy = chooseProxy();
                if (chosenProxy != null) {
                    return new Response<>((T) chosenProxy);
                } else {
                    return new Response<>(ResponseStatus.ERROR, "Nenhum servidor de proxy disponível");
                }

            case ATTACH:
                return attachProxy(request);

            case DETACH:
                return detachProxy(request);

            default:
                return new Response<>(ResponseStatus.ERROR, "O servidor de localização não suporta esta operação");
        }
    }

    private <T extends Serializable> Response<T> attachProxy(final Request<? extends Serializable> request) {
        final var item = request.getItem();
        if (item instanceof Notification && ((Notification) item).getNature().equals(Nature.PROXY)) {
            final var notification = (Notification) item;
            final var newProxyAddress = notification.getAddress();

            // Adicionar ao set
            proxiesAddresses.add(newProxyAddress);
            LOG.info("Recebido novo endereço de Proxy: {}", newProxyAddress);

            // Avisar outros servidores de proxy (Cheguei Brasil)
            proxiesAddresses.forEach(
                    proxy -> {
                        if (!proxy.equals(newProxyAddress)) {
                            send(proxy,
                                    new Request<>(Operation.ATTACH, new Notification(Nature.PROXY, newProxyAddress)));
                        }
                    });
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
            proxiesAddresses.remove(oldProxyAddress);
            LOG.info("Excluído endereço de Proxy: {}", oldProxyAddress);

            // Avisar outros servidores de proxy (Tchau tchau Brasil)
            proxiesAddresses.forEach(
                    proxy -> {
                        if (!proxy.equals(oldProxyAddress)) {
                            send(proxy,
                                    new Request<>(Operation.DETACH, new Notification(Nature.PROXY, oldProxyAddress)));
                        }
                    });
            return Response.ok();
        } else {
            return Response.error();
        }
    }

    private InetSocketAddress chooseProxy() {
        final var randomIndex = RANDOM.nextInt(proxiesAddresses.size() - 1);
        return proxiesAddresses.stream().collect(Collectors.toList()).get(randomIndex);
    }

}
