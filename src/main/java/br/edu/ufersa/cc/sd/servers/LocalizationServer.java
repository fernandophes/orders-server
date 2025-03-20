package br.edu.ufersa.cc.sd.servers;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.Combo;
import br.edu.ufersa.cc.sd.dto.Notification;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.Operation;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;

public class LocalizationServer extends AbstractServer {

    private static final Logger LOG = LoggerFactory.getLogger(LocalizationServer.class.getSimpleName());

    private Set<Combo> proxiesAddresses = new HashSet<>();
    private Combo proxyLeader;

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

    private void attachProxy(final InetSocketAddress newProxyServerSocketAddress,
            final InetSocketAddress newProxyRemoteAddress) {
        // Adicionar ao set
        final var newProxy = new Combo(newProxyServerSocketAddress, newProxyRemoteAddress);
        proxiesAddresses.add(newProxy);
        LOG.info("Recebido novo endereço de Proxy: {}", newProxyServerSocketAddress);

        // Avisar outros servidores de proxy (Cheguei Brasil)
        proxiesAddresses.forEach(
                combo -> {
                    if (!combo.equals(newProxy)) {
                        // Enviar novo proxy para o já existente
                        send(combo.getServerSocketAddress(), new Request<>(Operation.ATTACH,
                                new Notification(Nature.PROXY, newProxyServerSocketAddress, newProxyRemoteAddress)));

                        // Enviar o já existente para o novo
                        send(newProxy.getServerSocketAddress(), new Request<>(Operation.ATTACH,
                                new Notification(Nature.PROXY, combo.getServerSocketAddress(),
                                        combo.getRemoteAddress())));
                    }
                });

        // Define primeiro proxy registrado como líder
        if (proxyLeader == null) {
            proxyLeader = newProxy;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Serializable> Response<T> attachProxy(final Request<? extends Serializable> request) {
        final var item = request.getItem();
        if (item instanceof Notification && ((Notification) item).getNature().equals(Nature.PROXY)) {
            final var notification = (Notification) item;
            final var newProxyServerSocketAddress = notification.getServerSocketAddress();
            final var newProxyRemoteAddress = notification.getRemoteAddress();

            attachProxy(newProxyServerSocketAddress, newProxyRemoteAddress);

            return new Response<>((T) proxyLeader);
        } else {
            return Response.error();
        }
    }

    private void detachProxy(final InetSocketAddress oldProxyServerSocketAddress,
            final InetSocketAddress oldProxyRemoteAddress) {
        // Remover do set
        final var oldProxy = new Combo(oldProxyServerSocketAddress, oldProxyRemoteAddress);
        proxiesAddresses.remove(oldProxy);
        LOG.info("Excluído endereço de Proxy: {}", oldProxyServerSocketAddress);

        // Avisar outros servidores de proxy (Tchau tchau Brasil)
        proxiesAddresses.forEach(
                combo -> {
                    if (!combo.equals(oldProxy)) {
                        send(combo.getServerSocketAddress(),
                                new Request<>(Operation.DETACH,
                                        new Notification(Nature.PROXY, oldProxyServerSocketAddress,
                                                oldProxyRemoteAddress)));
                    }
                });

        // Caso o proxy removido seja o líder...
        if (oldProxy.equals(proxyLeader)) {
            if (proxiesAddresses.isEmpty()) {
                // ... anula o líder, caso não tenha mais nenhum
                proxyLeader = null;
            } else {
                // ou pega o próximo como líder
                proxyLeader = List.copyOf(proxiesAddresses).get(0);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Serializable> Response<T> detachProxy(final Request<? extends Serializable> request) {
        final var item = request.getItem();
        if (item instanceof Notification && ((Notification) item).getNature().equals(Nature.PROXY)) {
            final var notification = (Notification) item;
            final var oldProxyServerSocketAddress = notification.getServerSocketAddress();
            final var oldProxyRemoteAddress = notification.getRemoteAddress();

            detachProxy(oldProxyServerSocketAddress, oldProxyRemoteAddress);
            return new Response<>((T) proxyLeader);
        } else {
            return Response.error();
        }
    }

    private InetSocketAddress chooseProxy() {
        final var randomIndex = RANDOM.nextInt(proxiesAddresses.size());
        return proxiesAddresses.stream().collect(Collectors.toList()).get(randomIndex).getServerSocketAddress();
    }

}
