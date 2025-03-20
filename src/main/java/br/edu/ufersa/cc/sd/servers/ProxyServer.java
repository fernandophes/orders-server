package br.edu.ufersa.cc.sd.servers;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.contracts.RemoteProxy;
import br.edu.ufersa.cc.sd.dto.Combo;
import br.edu.ufersa.cc.sd.dto.Notification;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.Operation;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.exceptions.ConnectionException;
import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.services.CacheService;
import br.edu.ufersa.cc.sd.utils.JsonUtils;
import lombok.Getter;
import lombok.Setter;

public class ProxyServer extends AbstractServer implements RemoteProxy {

    private static final String PROXY = Nature.PROXY.getName();

    private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class.getSimpleName());

    private final CacheService cacheService;

    @Getter
    @Setter
    private Combo leader;
    private final InetSocketAddress localizationAddress;

    @Setter
    private InetSocketAddress applicationAddress;

    public ProxyServer(final InetSocketAddress localizationAddress, final InetSocketAddress applicationAddress)
            throws RemoteException {
        super(LOG, Nature.PROXY);
        this.localizationAddress = localizationAddress;
        this.applicationAddress = applicationAddress;
        cacheService = new CacheService();
    }

    @Override
    public void run() {
        try {
            getRemoteAddress(this);
        } catch (IOException | AlreadyBoundException e) {
            LOG.error("Não foi possível abrir um endereço remoto", e);
            return;
        }

        super.run();
        cacheService.setIdAddress(serverSocketAddress);

        // Notificar servidor de localização
        final var attachment = attachTo(localizationAddress);
        if (attachment.getStatus() == ResponseStatus.OK) {
            leader = (Combo) attachment.getItem();
        } else {
            close();
            throw new ConnectionException("Não foi possível se vincular ao servidor de localização");
        }

        // Notificar servidor de aplicação
        send(applicationAddress,
                new Request<>(Operation.ATTACH, new Notification(nature, serverSocketAddress, remoteAddress)));
    }

    @Override
    public void close() {
        // Notificar servidor de localização
        final var detachment = detachFrom(localizationAddress);
        if (detachment.getStatus() == ResponseStatus.OK) {
            for (final var replicaAddress : replicasAddresses) {
                try {
                    final var registry = LocateRegistry.getRegistry(replicaAddress.getHostString(),
                            replicaAddress.getPort());
                    final var replica = (RemoteProxy) registry.lookup(PROXY);

                    if (isLeader()) {
                        // Atualizar líder
                        replica.setLeader((Combo) detachment.getItem());
                    }
                } catch (final RemoteException | NotBoundException e) {
                    e.printStackTrace();
                }

            }
        } else {
            LOG.warn("Não foi possível se desvincular do servidor de localização");
        }

        // Notificar servidor de aplicação
        send(applicationAddress,
                new Request<>(Operation.DETACH, new Notification(nature, serverSocketAddress, remoteAddress)));
        super.close();
    }

    @Override
    public boolean isLeader() {
        return leader.getRemoteAddress().equals(remoteAddress);
    }

    public Order get(final Long code) {
        return cacheService.get(code).orElse(null);
    }

    private Order getOrFind(final Request<Order> request) {
        return cacheService.getOrFind(request.getItem().getCode(),
                () -> {
                    // Procurar primeiro nas réplicas
                    for (final var replicaAddress : replicasAddresses) {
                        try {
                            LOG.info("Buscando ordem #{} no proxy {}", request.getItem().getCode(),
                                    JsonUtils.write(replicaAddress));

                            final var registry = LocateRegistry.getRegistry(replicaAddress.getHostString(),
                                    replicaAddress.getPort());
                            final var replica = (RemoteProxy) registry.lookup(PROXY);
                            final var result = replica.get(request.getItem().getCode());

                            // Se encontrar a ordem, retorná-la
                            if (result != null) {
                                LOG.info("Ordem #{} encontada no proxy {}", request.getItem().getCode(),
                                        JsonUtils.write(replicaAddress));
                                return result;
                            }
                        } catch (final RemoteException | NotBoundException e) {
                            e.printStackTrace();
                        }
                    }

                    LOG.warn("Ordem #{} não encontada em nenhum cache", request.getItem().getCode());

                    // Se não encontrar em nenhuma, buscar no servidor de aplicação
                    final Response<Order> resp = redirectRequestToServer(request);
                    if (resp.getStatus() == ResponseStatus.ERROR) {
                        throw new NotFoundException();
                    }
                    return resp.getItem();
                });
    }

    @Override
    public void updateInCache(final Order order) {
        cacheService.update(order);
    }

    public Response<Order> updateIncludingCache(final Request<Order> request) {
        if (isLeader()) {
            // Editar na base de dados
            final Response<Order> response = redirectRequestToServer(request);

            // Atualizar no cache
            updateInCache(request.getItem());

            // Atualizar réplicas
            for (final var replicaAddress : replicasAddresses) {
                try {
                    final var registry = LocateRegistry.getRegistry(replicaAddress.getHostString(),
                            replicaAddress.getPort());
                    final var replica = (RemoteProxy) registry.lookup(PROXY);

                    replica.updateInCache(request.getItem());
                } catch (final RemoteException | NotBoundException e) {
                    e.printStackTrace();
                }
            }

            return response;
        } else {
            try {
                final var registry = LocateRegistry.getRegistry(leader.getRemoteAddress().getHostString(),
                        leader.getRemoteAddress().getPort());
                final var leaderStub = (RemoteProxy) registry.lookup(PROXY);

                return leaderStub.updateIncludingCache(request);
            } catch (final RemoteException | NotBoundException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public void deleteInCache(final Order order) {
        cacheService.delete(order);
    }

    @Override
    public Response<Order> deleteIncludingCache(final Request<Order> request) {
        if (isLeader()) {
            // Remover na base de dados
            final Response<Order> response = redirectRequestToServer(request);

            // Remover no cache
            cacheService.delete(request.getItem());

            // Atualizar réplicas deletando delas
            for (final var replicaAddress : replicasAddresses) {
                try {
                    final var registry = LocateRegistry.getRegistry(replicaAddress.getHostString(),
                            replicaAddress.getPort());
                    final var replica = (RemoteProxy) registry.lookup(PROXY);

                    replica.deleteInCache(request.getItem());
                } catch (final RemoteException | NotBoundException e) {
                    e.printStackTrace();
                }
            }

            return response;
        } else {
            try {
                final var registry = LocateRegistry.getRegistry(leader.getRemoteAddress().getHostString(),
                        leader.getRemoteAddress().getPort());
                final var leaderStub = (RemoteProxy) registry.lookup(PROXY);

                return leaderStub.deleteIncludingCache(request);
            } catch (final RemoteException | NotBoundException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private <O extends Serializable> Response<O> redirectRequestToServer(final Request<Order> request) {
        return send(applicationAddress, request);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T extends Serializable> Response<T> handleMessage(final Request<? extends Serializable> request) {
        final var orderRequest = (Request<Order>) request;
        switch (request.getOperation()) {
            case ATTACH:
                return attachProxy(request);

            case DETACH:
                return detachProxy(request);

            case LOCALIZE:
                return new Response<>(ResponseStatus.ERROR, "O servidor de Proxy não faz Localização");

            case FIND:
                final var result = getOrFind(orderRequest);
                if (result != null) {
                    return (Response<T>) new Response<>(result, "Ordem encontrada");
                } else {
                    return new Response<>(ResponseStatus.ERROR, "A ordem não existe na base de dados");
                }

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
            final var newProxyAddress = notification.getRemoteAddress();

            // Adicionar ao set
            replicasAddresses.add(newProxyAddress);
            LOG.info("Registrando réplica: {}", newProxyAddress);

            return Response.ok();
        } else {
            return Response.error();
        }
    }

    private <T extends Serializable> Response<T> detachProxy(final Request<? extends Serializable> request) {
        final var item = request.getItem();
        if (item instanceof Notification && ((Notification) item).getNature().equals(Nature.PROXY)) {
            final var notification = (Notification) item;
            final var oldProxyAddress = notification.getServerSocketAddress();

            // Adicionar ao set
            replicasAddresses.remove(oldProxyAddress);
            LOG.info("Excluído endereço de Proxy réplica: {}", oldProxyAddress);

            return Response.ok();
        } else {
            return Response.error();
        }
    }

}
